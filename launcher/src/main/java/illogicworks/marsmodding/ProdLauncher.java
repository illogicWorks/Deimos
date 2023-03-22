package illogicworks.marsmodding;

import java.io.*;
import java.lang.invoke.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Launches with deimos on production from a fatjar
 * @author altrisi
 *
 */
class ProdLauncher {
	private static final String LIB_LOCATION_PROP = "deimos.unpackedLibsPath";
	private static boolean launchedViaProdLauncher;

	public static void main(String[] args) throws Throwable {
		List<String> jars = listJars();
		String libPath = System.getProperty(LIB_LOCATION_PROP, ".deimoslibs");
		if (!isCacheValid(libPath)) {
			unpackLibs(libPath, jars);
		}
		MethodHandle entrypoint = launcher(libPath, jars);
		
		launchedViaProdLauncher = true;
		
		entrypoint.invokeExact(args);

		//KnotClient.main(args);
	}

	static MethodHandle launcher(String libPath, List<String> jars) throws IOException, ReflectiveOperationException {
		List<URL> urls = new ArrayList<>();
		for (String jar : jars) {
			Path jarPath = Path.of(libPath, jar);
			URL url = jarPath.toUri().toURL();
			urls.add(url);
		}
		URLClassLoader bootLoader = new URLClassLoader(urls.toArray(URL[]::new));
		Class<?> knotEntrypoint = bootLoader.loadClass("net.fabricmc.loader.impl.launch.knot.KnotClient");
		// Set context classloader for default ServiceLoader lookup to find our GameProvider on the correct loader
		Thread.currentThread().setContextClassLoader(bootLoader);
		return MethodHandles.publicLookup().findStatic(knotEntrypoint, "main", MethodType.methodType(void.class, String[].class));
	}

	public static boolean wasUsed() {
		return launchedViaProdLauncher;
	}
	
	public static List<URL> classPath() {
		assert !wasUsed() : "Asked for classpath when ProdLauncher wasn't used";
		return null; // TODO
	}
	
	public static List<String> listJars() throws IOException {
		String path = "/META-INF/libs";
		URI uri;
		try {
			uri = ProdLauncher.class.getResource(path).toURI();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}

        if (!uri.getScheme().equals("jar")) {
            throw new UnsupportedOperationException("Using ProdLauncher in exploded environment");
        }

        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of())) {
        	Path libsFolder = fileSystem.getPath(path);
        	try (var stream = Files.list(libsFolder)) {
            	return stream
            			.map(Path::getFileName)
            			.map(Path::toString)
            			.collect(Collectors.toList());
            }
        }
    }
	
	static boolean isCacheValid(String libPath) throws IOException {
		// check if unpacked
		Path cacheLock = Path.of(libPath, "versionlock");
		if (Files.exists(cacheLock) && Files.readString(cacheLock).equals(ProdLauncher.class.getPackage().getImplementationVersion())) {
			// We're good to go, do nothing
			return true;
		} else {
			return false;
		}
	}

	static void unpackLibs(String libPath, List<String> libs) throws IOException {
		String version = ProdLauncher.class.getPackage().getImplementationVersion();

		System.out.println("Unpacking libraries for first launch of Deimos " + version + "...");

		Path libsDir = Path.of(libPath);
		Files.createDirectories(libsDir);

		// empty possible existing cache folder
		try (var contents = Files.newDirectoryStream(libsDir)) {
			for (Path p : contents) {
				if (Files.isDirectory(p)) {
					System.err.println("Found directory when cleaning .deimoslibs folder!");
				} else {
					Files.delete(p);
				}
			}
		}

		for (String lib : libs) {
			Path target = Path.of(libPath, lib);
			System.out.println("Unpacking " + lib + " to " + target.toAbsolutePath() + "...");
			try (InputStream origin = ProdLauncher.class.getResourceAsStream("/META-INF/libs/" + lib)) {
				Files.copy(origin, target);
			}
		}

		Files.writeString(libsDir.resolve("versionlock"), version);
	}

}
