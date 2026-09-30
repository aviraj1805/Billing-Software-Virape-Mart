package com.virpemart.billing.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A piece of database work that returns a result. Used with {@link Database#inTransaction(SqlWork)}.
 *
 * @param <T> the type of result
 */
@FunctionalInterface
public interface SqlWork<T> {

    T run(Connection connection) throws SQLException;
}
