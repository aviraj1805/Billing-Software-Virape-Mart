package com.virpemart.billing.service;

import java.time.LocalDate;
import java.util.List;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.model.AuditEntry;
import com.virpemart.billing.repository.AuditRepository;

/** Reads the audit log: the permanent record of rate changes, cancelled bills, edits, settings and backups. */
public final class AuditService {

    private final Database database;
    private final AuditRepository audit;
    private final Session session;

    public AuditService(Database database, AuditRepository audit, Session session) {
        this.database = database;
        this.audit = audit;
        this.session = session;
    }

    /**
     * Audit records, newest first. Owner only.
     *
     * @param from  first day, or null for no start
     * @param to    last day (included), or null for no end
     * @param text  part of the details to look for, or null
     * @throws ValidationException (field "from") if the first day is after the last day
     */
    public List<AuditEntry> search(LocalDate from, LocalDate to, String text, int limit) {
        session.requireOwner();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("from", "The first date is after the last date. Please check the dates.");
        }
        String cleanText = Texts.clean(text);
        return database.query(c -> audit.search(c, from, to, cleanText, limit));
    }
}
