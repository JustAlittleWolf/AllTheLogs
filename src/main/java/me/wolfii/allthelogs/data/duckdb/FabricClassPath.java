package me.wolfii.allthelogs.data.duckdb;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Adds a jar to the running class loader. Fabric's Knot loader is used in game; tests fall back to
 * {@link URLClassLoader}.
 */
public final class FabricClassPath implements DuckDbJdbcInstaller.ClassPathAppender {
    private static boolean addViaFabric(Path jar) {
        try {
            Class<?> launcherBase = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Object launcher = launcherBase.getMethod("getLauncher").invoke(null);
            launcher.getClass().getMethod("addToClassPath", Path.class).invoke(launcher, jar);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean addViaKnot(URL url) {
        ClassLoader loader = FabricClassPath.class.getClassLoader();
        for (Class<?> type = loader.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : new String[]{"addUrlFwd", "addURL", "addUrl"}) {
                try {
                    Method method = type.getDeclaredMethod(name, URL.class);
                    method.setAccessible(true);
                    method.invoke(loader, url);
                    return true;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean addViaUrlClassLoader(URL url) throws Exception {
        ClassLoader loader = FabricClassPath.class.getClassLoader();
        if (loader instanceof URLClassLoader urlLoader) {
            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
            addUrl.invoke(urlLoader, url);
            return true;
        }
        return false;
    }

    @Override
    public void add(Path jar) throws Exception {
        URL url = jar.toUri().toURL();
        if (addViaFabric(jar) || addViaKnot(url) || addViaUrlClassLoader(url)) {
            return;
        }
        throw new IllegalStateException("could not add " + jar.getFileName() + " to the classpath");
    }
}
