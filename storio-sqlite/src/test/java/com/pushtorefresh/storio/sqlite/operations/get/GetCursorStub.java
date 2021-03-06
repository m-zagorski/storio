package com.pushtorefresh.storio.sqlite.operations.get;

import android.database.Cursor;
import android.support.annotation.NonNull;

import com.pushtorefresh.storio.sqlite.Changes;
import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.pushtorefresh.storio.sqlite.queries.Query;
import com.pushtorefresh.storio.sqlite.queries.RawQuery;
import com.pushtorefresh.storio.test.ObservableBehaviorChecker;

import rx.Observable;
import rx.functions.Action1;

import static java.util.Collections.singleton;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetCursorStub {

    @NonNull
    final StorIOSQLite storIOSQLite;

    @NonNull
    private final StorIOSQLite.Internal internal;

    @NonNull
    final Query query;

    @NonNull
    final RawQuery rawQuery;

    @NonNull
    final GetResolver<Cursor> getResolverForCursor;

    @NonNull
    private final Cursor cursor;

    @SuppressWarnings("unchecked")
    private GetCursorStub() {
        storIOSQLite = mock(StorIOSQLite.class);
        internal = mock(StorIOSQLite.Internal.class);

        when(storIOSQLite.internal())
                .thenReturn(internal);

        query = mock(Query.class);
        when(query.table()).thenReturn("test_table");

        rawQuery = mock(RawQuery.class);
        when(rawQuery.observesTables()).thenReturn(singleton("test_table"));

        getResolverForCursor = mock(GetResolver.class);
        cursor = mock(Cursor.class);

        when(storIOSQLite.get())
                .thenReturn(new PreparedGet.Builder(storIOSQLite));

        when(storIOSQLite.observeChangesInTables(eq(singleton(query.table()))))
                .thenReturn(Observable.<Changes>empty());

        assertNotNull(rawQuery.observesTables());

        when(storIOSQLite.observeChangesInTables(rawQuery.observesTables()))
                .thenReturn(Observable.<Changes>empty());

        when(getResolverForCursor.performGet(storIOSQLite, query))
                .thenReturn(cursor);

        when(getResolverForCursor.performGet(storIOSQLite, rawQuery))
                .thenReturn(cursor);
    }

    @NonNull
    static GetCursorStub newInstance() {
        return new GetCursorStub();
    }

    void verifyQueryBehaviorForCursor(@NonNull Cursor actualCursor) {
        assertNotNull(actualCursor);
        verify(storIOSQLite).get();
        verify(getResolverForCursor).performGet(storIOSQLite, query);
        assertSame(cursor, actualCursor);
        verifyNoMoreInteractions(storIOSQLite, internal, cursor);
    }

    void verifyQueryBehaviorForCursor(@NonNull Observable<Cursor> observable) {
        new ObservableBehaviorChecker<Cursor>()
                .observable(observable)
                .expectedNumberOfEmissions(1)
                .testAction(new Action1<Cursor>() {
                    @Override
                    public void call(Cursor cursor) {
                        // Get Operation should be subscribed to changes of tables from Query
                        verify(storIOSQLite).observeChangesInTables(eq(singleton(query.table())));
                        verifyQueryBehaviorForCursor(cursor);
                    }
                })
                .checkBehaviorOfObservable();
    }

    void verifyRawQueryBehaviorForCursor(@NonNull Cursor actualCursor) {
        assertNotNull(actualCursor);
        verify(storIOSQLite, times(1)).get();
        verify(getResolverForCursor, times(1)).performGet(storIOSQLite, rawQuery);
        assertSame(cursor, actualCursor);
        verifyNoMoreInteractions(storIOSQLite, internal, cursor);
    }

    void verifyRawQueryBehaviorForCursor(@NonNull Observable<Cursor> observable) {
        new ObservableBehaviorChecker<Cursor>()
                .observable(observable)
                .expectedNumberOfEmissions(1)
                .testAction(new Action1<Cursor>() {
                    @Override
                    public void call(Cursor cursor) {
                        // Get Operation should be subscribed to changes of tables from Query
                        verify(storIOSQLite).observeChangesInTables(rawQuery.observesTables());
                        verifyRawQueryBehaviorForCursor(cursor);
                    }
                })
                .checkBehaviorOfObservable();

        assertNotNull(rawQuery.observesTables());
    }
}
