package edu.cit.dasig_core.core.perf;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the SQL statements Hibernate runs on the current thread.
 * Used by {@link RequestTimingFilter} to log how many queries each request needs.
 */
public class QueryCounter implements StatementInspector {

    private static final ThreadLocal<Integer> COUNT = ThreadLocal.withInitial(() -> 0);

    @Override
    public String inspect(String sql) {
        COUNT.set(COUNT.get() + 1);
        return sql;
    }

    public static void reset() {
        COUNT.set(0);
    }

    public static int count() {
        return COUNT.get();
    }
}
