package com.pushtorefresh.storio.contentresolver.operations.put;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.pushtorefresh.storio.contentresolver.ContentResolverTypeMapping;
import com.pushtorefresh.storio.contentresolver.StorIOContentResolver;
import com.pushtorefresh.storio.operations.internal.OnSubscribeExecuteAsBlocking;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.pushtorefresh.storio.internal.Environment.throwExceptionIfRxJavaIsNotAvailable;

/**
 * Prepared Put Operation for collection of objects.
 *
 * @param <T> type of objects.
 */
public final class PreparedPutCollectionOfObjects<T> extends PreparedPut<PutResults<T>> {

    @NonNull
    private final Collection<T> objects;

    @Nullable
    private final PutResolver<T> explicitPutResolver;

    PreparedPutCollectionOfObjects(@NonNull StorIOContentResolver storIOContentResolver,
                                   @NonNull Collection<T> objects,
                                   @Nullable PutResolver<T> explicitPutResolver) {
        super(storIOContentResolver);
        this.objects = objects;
        this.explicitPutResolver = explicitPutResolver;
    }

    /**
     * Executes Put Operation immediately in current thread.
     * <p>
     * Notice: This is blocking I/O operation that should not be executed on the Main Thread,
     * it can cause ANR (Activity Not Responding dialog), block the UI and drop animations frames.
     * So please, call this method on some background thread. See {@link WorkerThread}.
     *
     * @return non-null result of Put Operation.
     */
    @SuppressWarnings("unchecked")
    @WorkerThread
    @NonNull
    @Override
    public PutResults<T> executeAsBlocking() {
        final StorIOContentResolver.Internal internal = storIOContentResolver.internal();

        // Nullable
        final List<SimpleImmutableEntry<T, PutResolver<T>>> objectsAndPutResolvers;

        if (explicitPutResolver != null) {
            objectsAndPutResolvers = null;
        } else {
            objectsAndPutResolvers = new ArrayList<SimpleImmutableEntry<T, PutResolver<T>>>(objects.size());

            for (final T object : objects) {
                final ContentResolverTypeMapping<T> typeMapping
                        = (ContentResolverTypeMapping<T>) internal.typeMapping(object.getClass());

                if (typeMapping == null) {
                    throw new IllegalStateException("One of the objects from the collection does not have type mapping: " +
                            "object = " + object + ", object.class = " + object.getClass() + "," +
                            "ContentProvider was not affected by this operation, please add type mapping for this type");
                }

                objectsAndPutResolvers.add(new SimpleImmutableEntry<T, PutResolver<T>>(
                        object,
                        typeMapping.putResolver()
                ));
            }
        }

        final Map<T, PutResult> results = new HashMap<T, PutResult>(objects.size());

        if (explicitPutResolver != null) {
            for (final T object : objects) {
                final PutResult putResult = explicitPutResolver.performPut(storIOContentResolver, object);
                results.put(object, putResult);
            }
        } else {
            for (final SimpleImmutableEntry<T, PutResolver<T>> objectAndPutResolver : objectsAndPutResolvers) {
                final T object = objectAndPutResolver.getKey();
                final PutResolver<T> putResolver = objectAndPutResolver.getValue();

                final PutResult putResult = putResolver.performPut(storIOContentResolver, object);
                results.put(object, putResult);
            }
        }

        return PutResults.newInstance(results);
    }

    /**
     * Creates {@link Observable} which will perform Put Operation and send result to observer.
     * <p>
     * Returned {@link Observable} will be "Cold Observable", which means that it performs
     * put only after subscribing to it. Also, it emits the result once.
     * <p>
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>Operates on {@link Schedulers#io()}.</dd>
     * </dl>
     *
     * @return non-null {@link Observable} which will perform Put Operation.
     * and send result to observer.
     */
    @NonNull
    @Override
    public Observable<PutResults<T>> createObservable() {
        throwExceptionIfRxJavaIsNotAvailable("createObservable()");

        return Observable
                .create(OnSubscribeExecuteAsBlocking.newInstance(this))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Builder for {@link PreparedPutCollectionOfObjects}.
     *
     * @param <T> type of objects to put.
     */
    public static final class Builder<T> {

        @NonNull
        private final StorIOContentResolver storIOContentResolver;

        @NonNull
        private final Collection<T> objects;

        @Nullable
        private PutResolver<T> putResolver;

        public Builder(@NonNull StorIOContentResolver storIOContentResolver, @NonNull Collection<T> objects) {
            this.storIOContentResolver = storIOContentResolver;
            this.objects = objects;
        }

        /**
         * Optional: Specifies resolver for Put Operation
         * that should define behavior of Put Operation: insert or update
         * of the objects.
         * <p>
         * Can be set via {@link ContentResolverTypeMapping},
         * If value is not set via {@link ContentResolverTypeMapping}
         * or explicitly — exception will be thrown.
         *
         * @param putResolver nullable resolver for Put Operation.
         * @return builder.
         */
        @NonNull
        public Builder<T> withPutResolver(@NonNull PutResolver<T> putResolver) {
            this.putResolver = putResolver;
            return this;
        }

        /**
         * Builds new instance of {@link PreparedPutCollectionOfObjects}.
         *
         * @return new instance of {@link PreparedPutCollectionOfObjects}.
         */
        @NonNull
        public PreparedPutCollectionOfObjects<T> prepare() {
            return new PreparedPutCollectionOfObjects<T>(
                    storIOContentResolver,
                    objects,
                    putResolver
            );
        }
    }
}
