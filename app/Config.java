package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private final Properties props = new Properties();
    private final File root;

    public Config(File rootDir, File propertiesFile) {
        this.root = rootDir;
        try (FileInputStream fis = new FileInputStream(propertiesFile)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + propertiesFile.getAbsolutePath(), e);
        }
    }

    public boolean getBoolean(String key, boolean defVal) {
        String v = props.getProperty(key);
        if (v == null)
            return defVal;
        return Boolean.parseBoolean(v.trim());
    }

    public String getString(String key, String defVal) {
        String v = props.getProperty(key);
        return v != null ? v.trim() : defVal;
    }

    public boolean isNNEnabled() {
        return getBoolean("enable_nn_scoring", false);
    }

    public String getNNMode() {
        return getString("nn_mode", "oneshot"); // oneshot | persistent
    }

    public long getNNTimeoutMs() {
        try {
            return Long.parseLong(getString("nn_timeout_ms", "0"));
        } catch (Exception e) {
            return 0L;
        }
    }

    public double[] getDoubles2(String key, double d0, double d1) {
        String v = props.getProperty(key);
        if (v == null)
            return new double[] { d0, d1 };
        String[] parts = v.split(",");
        if (parts.length < 2)
            return new double[] { d0, d1 };
        try {
            return new double[] { Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()) };
        } catch (Exception e) {
            return new double[] { d0, d1 };
        }
    }

    public File resolveUnderRoot(String relative) {
        return new File(root, relative.replace('/', File.separatorChar));
    }

    public String getPythonExe() {
        return getString("python_exe", "python");
    }
}
