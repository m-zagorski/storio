package com.pushtorefresh.storio.sqlite.operations.get;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.pushtorefresh.storio.operations.internal.MapSomethingToExecuteAsBlocking;
import com.pushtorefresh.storio.operations.internal.OnSubscribeExecuteAsBlocking;
import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;

import java.util.HashSet;
import java.util.Set;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.pushtorefresh.storio.internal.Checks.checkNotNull;
import static com.pushtorefresh.storio.internal.Environment.throwExceptionIfRxJavaIsNotAvailable;

/**
 * Prepared Get Operation for {@link StorIOSQLite}.
 */
public final class PreparedGetCursor extends PreparedGet<Cursor> {

    @NonNull
    private final GetResolver<Cursor> getResolver;

    PreparedGetCursor(@NonNull StorIOSQLite storIOSQLite,
                      @NonNull Query query,
                      @NonNull GetResolver<Cursor> getResolver) {
        super(storIOSQLite, query);
        this.getResolver = getResolver;
    }

    PreparedGetCursor(@NonNull StorIOSQLite storIOSQLite,
                      @NonNull RawQuery rawQuery,
                      @NonNull GetResolver<Cursor> getResolver) {
        super(storIOSQLite, rawQuery);
        this.getResolver = getResolver;
    }

    /**
     * Executes Get Operation immediately in current thread.
     * <p>
     * Notice: This is blocking I/O operation that should not be executed on the Main Thread,
     * it can cause ANR (Activity Not Responding dialog), block the UI and drop animations frames.
     * So please, call this method on some background thread. See {@link WorkerThread}.
     *
     * @return non-null {@link Cursor}, can be empty.
     */
    @WorkerThread
    @NonNull
    public Cursor executeAsBlocking() {
        if (query != null) {
            return getResolver.performGet(storIOSQLite, query);
        } else if (rawQuery != null) {
            return getResolver.performGet(storIOSQLite, rawQuery);
        } else {
            throw new IllegalStateException("Please specify query");
        }
    }

    /**
     * Creates "Hot" {@link Observable} which will be subscribed to changes of tables from query
     * and will emit result each time change occurs.
     * <p>
     * First result will be emitted immediately after subscription,
     * other emissions will occur only if changes of tables from query will occur during lifetime of
     * the {@link Observable}.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>Operates on {@link Schedulers#io()}.</dd>
     * </dl>
     * <p>
     * Please don't forget to unsubscribe from this {@link Observable} because
     * it's "Hot" and endless.
     *
     * @return non-null {@link Observable} which will emit non-null
     * list with mapped results and will be subscribed to changes of tables from query.
     */
    @NonNull
    @Override
    public Observable<Cursor> createObservable() {
        throwExceptionIfRxJavaIsNotAvailable("createObservable()");

        final Set<String> tables;

        if (query != null) {
            tables = new HashSet<String>(1);
            tables.add(query.table());
        } else if (rawQuery != null) {
            tables = rawQuery.observesTables();
        } else {
            throw new IllegalStateException("Please specify query");
        }

        if (!tables.isEmpty()) {
            return storIOSQLite
                    .observeChangesInTables(tables) // each change triggers executeAsBlocking
                    .map(MapSomethingToExecuteAsBlocking.newInstance(this))
                    .startWith(Observable.create(OnSubscribeExecuteAsBlocking.newInstance(this))) // start stream with first query result
                    .subscribeOn(Schedulers.io());
        } else {
            return Observable
                    .create(OnSubscribeExecuteAsBlocking.newInstance(this))
                    .subscribeOn(Schedulers.io());
        }
    }

    /**
     * Builder for {@link PreparedGetCursor}.
     * <p>
     * Required: You should specify query by call
     * {@link #withQuery(Query)} or {@link #withQuery(RawQuery)}.
     */
    public static final class Builder {

        @NonNull
        private final StorIOSQLite storIOSQLite;

        Builder(@NonNull StorIOSQLite storIOSQLite) {
            this.storIOSQLite = storIOSQLite;
        }

        /**
         * Required: Specifies {@link Query} for Get Operation.
         *
         * @param query query.
         * @return builder.
         * @see Query
         */
        @NonNull
        public CompleteBuilder withQuery(@NonNull Query query) {
            checkNotNull(query, "Please specify query");
            return new CompleteBuilder(storIOSQLite, query);
        }

        /**
         * Required: Specifies {@link RawQuery} for Get Operation,
         * you can use it for "joins" and same constructions which are not allowed for {@link Query}.
         *
         * @param rawQuery query.
         * @return builder.
         * @see RawQuery
         */
        @NonNull
        public CompleteBuilder withQuery(@NonNull RawQuery rawQuery) {
            checkNotNull(rawQuery, "Please specify rawQuery");
            return new CompleteBuilder(storIOSQLite, rawQuery);
        }
    }

    /**
     * Compile-time safe part of builder for {@link PreparedGetCursor}.
     */
    public static final class CompleteBuilder {

        private static final GetResolver<Cursor> STANDARD_GET_RESOLVER = new DefaultGetResolver<Cursor>() {
            @NonNull
            @Override
            public Cursor mapFromCursor(@NonNull Cursor cursor) {
                return cursor; // no modifications
            }
        };

        @NonNull
        private final StorIOSQLite storIOSQLite;

        private final Query query;

        private final RawQuery rawQuery;

        private GetResolver<Cursor> getResolver;

        CompleteBuilder(@NonNull StorIOSQLite storIOSQLite, @NonNull Query query) {
            this.storIOSQLite = storIOSQLite;
            this.query = query;
            this.rawQuery = null;
        }

        CompleteBuilder(@NonNull StorIOSQLite storIOSQLite, @NonNull RawQuery rawQuery) {
            this.storIOSQLite = storIOSQLite;
            this.rawQuery = rawQuery;
            this.query = null;
        }

        /**
         * Optional: Specifies Get Resolver for operation.
         * If no value is set, builder will use resolver that
         * simply redirects query to {@link StorIOSQLite}.
         *
         * @param getResolver nullable GetResolver for Get Operation.
         * @return builder.
         */
        @NonNull
        public CompleteBuilder withGetResolver(@Nullable GetResolver<Cursor> getResolver) {
            this.getResolver = getResolver;
            return this;
        }

        /**
         * Prepares Get Operation.
         *
         * @return {@link PreparedGetCursor} instance.
         */
        @NonNull
        public PreparedGetCursor prepare() {
            if (getResolver == null) {
                getResolver = STANDARD_GET_RESOLVER;
            }

            if (query != null) {
                return new PreparedGetCursor(storIOSQLite, query, getResolver);
            } else if (rawQuery != null) {
                return new PreparedGetCursor(storIOSQLite, rawQuery, getResolver);
            } else {
                throw new IllegalStateException("Please specify query");
            }
        }
    }
}
