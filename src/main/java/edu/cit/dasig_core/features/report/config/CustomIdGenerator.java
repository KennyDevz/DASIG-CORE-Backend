package edu.cit.dasig_core.features.report.config;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class CustomIdGenerator implements IdentifierGenerator {

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        Long seqValue = null;

        // Use the explicit doWork interface to cleanly handle checked SQLExceptions
        try {
            Connection connection = session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
            try (PreparedStatement statement = connection.prepareStatement("SELECT nextval('pr_id_seq')");
                 ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    seqValue = resultSet.getLong(1);
                } else {
                    throw new RuntimeException("Failed to fetch next sequence value from Supabase");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while generating custom ID", e);
        }

        // Get the current year (e.g., 2026)
        int currentYear = LocalDate.now().getYear();

        // Format the ID as PR-2026-0001
        return String.format("PR-%d-%04d", currentYear, seqValue);
    }
}