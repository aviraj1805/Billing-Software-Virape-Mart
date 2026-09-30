package com.virpemart.billing.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A piece of database work with no result. Used with {@link Database#runInTransaction(SqlAction)}. */
@FunctionalInterface
public interface SqlAction {

    void run(Connection connection) throws SQLException;
}
