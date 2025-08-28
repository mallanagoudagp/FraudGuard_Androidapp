package app;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Logger utility for FraudGuard agents and fusion engine.
 * Provides structured logging to CSV and JSON formats for analysis.
 */
public class Logger {

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    private static final String LOG_DIR = "logs";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private final String logFilePath;
    private final LogLevel minLogLevel;
    private final boolean enableConsole;

    public Logger(String logFileName, LogLevel minLogLevel, boolean enableConsole) {
        this.logFilePath = LOG_DIR + File.separator + logFileName;
        this.minLogLevel = minLogLevel;
        this.enableConsole = enableConsole;

        // Create log directory if it doesn't exist
        File logDirFile = new File(LOG_DIR);
        if (!logDirFile.exists()) {
            logDirFile.mkdirs();
        }
    }

    public Logger(String logFileName) {
        this(logFileName, LogLevel.INFO, true);
    }

    /**
     * Log agent result with detailed information
     */
    public void logAgentResult(String agentName, Agent.AgentResult result) {
        if (shouldLog(LogLevel.INFO)) {
            String message = String.format(
                    "Agent=%s, Score=%.3f, Explanations=%s, Timestamp=%d",
                    agentName,
                    result.score,
                    String.join("; ", result.explanations),
                    result.timestamp);
            log(LogLevel.INFO, "AGENT_RESULT", message);
        }
    }

    /**
     * Log fusion engine result
     */
    public void logFusionResult(FusionEngine.FusionResult result,
            Double touchScore, Double typingScore, Double usageScore) {
        if (shouldLog(LogLevel.INFO)) {
            String message = String.format(
                    "FinalScore=%.3f, RiskLevel=%s, TouchScore=%s, TypingScore=%s, UsageScore=%s, Explanations=%s",
                    result.finalScore,
                    result.riskLevel,
                    formatScore(touchScore),
                    formatScore(typingScore),
                    formatScore(usageScore),
                    String.join("; ", result.explanations));
            log(LogLevel.INFO, "FUSION_RESULT", message);
        }
    }

    /**
     * Log response action taken
     */
    public void logResponseAction(FusionEngine.RiskLevel riskLevel, String action, String details) {
        if (shouldLog(LogLevel.INFO)) {
            String message = String.format(
                    "RiskLevel=%s, Action=%s, Details=%s",
                    riskLevel,
                    action,
                    details);
            log(LogLevel.INFO, "RESPONSE_ACTION", message);
        }
    }

    /**
     * Log general message with level
     */
    public void log(LogLevel level, String category, String message) {
        if (!shouldLog(level)) {
            return;
        }

        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format(
                "%s [%s] %s: %s",
                timestamp,
                level,
                category,
                message);

        // Write to file
        writeToFile(logEntry);

        // Write to console if enabled
        if (enableConsole) {
            System.out.println(logEntry);
        }
    }

    /**
     * Log CSV record for agent scores (for analysis)
     */
    public void logCsvRecord(String agentName, double score, List<String> features,
            FusionEngine.FusionResult fusionResult) {
        String csvFilePath = logFilePath.replace(".log", "_data.csv");

        // Check if file exists to write header
        File csvFile = new File(csvFilePath);
        boolean writeHeader = !csvFile.exists();

        try (FileWriter writer = new FileWriter(csvFile, true)) {
            if (writeHeader) {
                writer.write("timestamp,agent,score,features,final_score,risk_level,explanations\n");
            }

            String timestamp = dateFormat.format(new Date());
            String featuresStr = String.join("|", features);
            String explanationsStr = String.join("|", fusionResult.explanations);

            writer.write(String.format(
                    "%s,%s,%.3f,\"%s\",%.3f,%s,\"%s\"\n",
                    timestamp,
                    agentName,
                    score,
                    featuresStr,
                    fusionResult.finalScore,
                    fusionResult.riskLevel,
                    explanationsStr));

        } catch (IOException e) {
            System.err.println("Failed to write CSV log: " + e.getMessage());
        }
    }

    /**
     * Write JSON log entry for structured analysis
     */
    public void logJson(String category, Object data) {
        if (!shouldLog(LogLevel.INFO)) {
            return;
        }

        String jsonFilePath = logFilePath.replace(".log", ".json");
        String timestamp = dateFormat.format(new Date());

        try (FileWriter writer = new FileWriter(jsonFilePath, true)) {
            writer.write(String.format(
                    "{\"timestamp\":\"%s\",\"category\":\"%s\",\"data\":%s}\n",
                    timestamp,
                    category,
                    data.toString()));
        } catch (IOException e) {
            System.err.println("Failed to write JSON log: " + e.getMessage());
        }
    }

    // Convenience methods
    public void info(String category, String message) {
        log(LogLevel.INFO, category, message);
    }

    public void warn(String category, String message) {
        log(LogLevel.WARN, category, message);
    }

    public void error(String category, String message) {
        log(LogLevel.ERROR, category, message);
    }

    public void debug(String category, String message) {
        log(LogLevel.DEBUG, category, message);
    }

    private boolean shouldLog(LogLevel level) {
        return level.ordinal() >= minLogLevel.ordinal();
    }

    private String formatScore(Double score) {
        return score != null ? String.format("%.3f", score) : "null";
    }

    private void writeToFile(String logEntry) {
        try (FileWriter writer = new FileWriter(logFilePath, true)) {
            writer.write(logEntry + "\n");
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Rotate log files when they get too large
     */
    public void rotateIfNeeded(long maxSizeBytes) {
        File logFile = new File(logFilePath);
        if (logFile.exists() && logFile.length() > maxSizeBytes) {
            String backupPath = logFilePath + "." + System.currentTimeMillis();
            try {
                logFile.renameTo(new File(backupPath));
                info("LOGGER", "Log file rotated to: " + backupPath);
            } catch (Exception e) {
                error("LOGGER", "Failed to rotate log file: " + e.getMessage());
            }
        }
    }
}
