package illogicworks.marsmodding;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.LibClassifier;
import net.fabricmc.loader.impl.game.LibClassifier.LibraryType;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.SystemProperties;

import java.io.IOException;
import java.lang.invoke.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeimosGameProvider implements GameProvider {
	private Arguments arguments;
	private final List<Path> gameJars = new ArrayList<>();

	private static final GameTransformer TRANSFORMER = new GameTransformer(); // TODO main entrypoints

	@Override
	public String getGameId() {
		return "mars";
	}

	@Override
	public String getGameName() {
		return "Mars";
	}

	@Override
	public String getRawGameVersion() {
		return "0.0.0+unknown";
	}

	@Override
	public String getNormalizedGameVersion() {
		return getRawGameVersion();
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(
				getGameId(),
				getNormalizedGameVersion())
				.setName(getGameName())
				.addAuthor("Missouri State", Map.of())
				.addContributor("UPC", Map.of())
				.setDescription("The Mars program")
				.setEnvironment(ModEnvironment.UNIVERSAL);

		return List.of(new BuiltinMod(gameJars, metadata.build()));
	}

	@Override
	public String getEntrypoint() {
		return ""; // TODO probably
	}

	@Override
	public Path getLaunchDirectory() {
		if (arguments == null) {
			return Paths.get(".");
		}
		return getLaunchDirectory(arguments);
	}

	@Override
	public boolean isObfuscated() {
		return false;
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return false;
	}

	@Override
	public boolean isEnabled() {
		return System.getProperty("mars.skipMarsProvider") == null;
	}
	
	enum MarsLibrary implements LibraryType {
		MARS("Mars.class"),
		DEIMOS(DeimosGameProvider.class.getCanonicalName().replace('.', '/') + ".class");
		private final String[] containedPaths;
		private MarsLibrary(String path) {
			this.containedPaths = new String[] {path};
		}
		@Override
		public String[] getPaths() {
			return containedPaths;
		}
		@Override
		public boolean isApplicable(EnvType env) { return true; } // no different envs in Mars
	}

	@Override
	public boolean locateGame(FabricLauncher launcher, String[] args) {
		this.arguments = new Arguments();
		arguments.parse(args);
		
		// On dev env, process the classpath to ensure everything is in the correct classloaders
		if (Boolean.getBoolean(SystemProperties.DEVELOPMENT)) {
			EnvType envType = launcher.getEnvironmentType();
			try {
				LibClassifier<MarsLibrary> classifier = new LibClassifier<>(MarsLibrary.class, envType, this);
				classifier.process(launcher.getClassPath());
				
				// Add other unknown classpath entries to gamejars.
				// This ensures they're available in the runtime classpath
				gameJars.addAll(classifier.getUnmatchedOrigins());
				
				if (classifier.has(MarsLibrary.MARS)) {
					// Mars has already been added to gamejars, no need to search for it
					return true;
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to identify libs in dev environment!");
			}
		}
		// Outside dev env, match any Mars*.jar, but only one
		String gamePath;
		if ((gamePath = System.getProperty(SystemProperties.GAME_JAR_PATH)) != null) {
			Path gameJar = Path.of(gamePath);
			if (!Files.exists(gameJar) || Files.isDirectory(gameJar)) {
				throw new IllegalStateException("Mars jar couldn't be located in provided " + SystemProperties.GAME_JAR_PATH + " (" + gameJar + ")");
			}
			gameJars.add(gameJar);
			return true;
		}
		try (Stream<Path> children = Files.list(Path.of("."))) {
			List<Path> paths = children
					.filter(p -> p.getFileName().toString().startsWith("Mars") && p.getFileName().toString().endsWith(".jar"))
					.collect(Collectors.toList());
			if (paths.size() == 0) {
				throw new IllegalStateException("Mars jar was not located! Make sure it's in the run folder (current one)");
			}
			if (paths.size() > 1) {
				throw new IllegalStateException("Multiple potential Mars jars found! Only one jar starting with 'Mars' in the folder is supported!");
			}
			gameJars.add(paths.get(0));
		} catch (IOException e) {
			throw new IllegalStateException("Exception while trying to locate Mars!", e);
		}
		return true;
	}

	private static Path getLaunchDirectory(Arguments argMap) {
		return Paths.get(argMap.getOrDefault("gameDir", "."));
	}

	@Override
	public void initialize(FabricLauncher launcher) {
		TRANSFORMER.locateEntrypoints(launcher, gameJars);
	}

	@Override
	public Arguments getArguments() {
		return arguments;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments == null) return new String[0];
		// we have nothing to sanitize
		return arguments.toArray();
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public void unlockClassPath(FabricLauncher launcher) {
		for (Path gameJar : gameJars)
			launcher.addToClassPath(gameJar);
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = "Mars";
		
		MethodHandle invoker;
		try {
			Class<?> c = loader.loadClass(targetClass);
			invoker = MethodHandles.lookup().findStatic(c, "main", MethodType.methodType(void.class, String[].class));
		} catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
			throw new FormattedException("Failed to invoke Mars!", e);
		}
		
		try {
			invoker.invokeExact(arguments.toArray());
		} catch (Throwable t) {
			throw new FormattedException("Mars has crashed!", t);
		}
	}
}
