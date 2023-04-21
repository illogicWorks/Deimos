package illogicworks.marsmodding;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import javax.swing.JOptionPane;

public class JavaCheck {
	private static final int REQUIRED_VERSION = 11;

	static void ensureJava11() {
		// Runtime.version() was added on Java 9
		@SuppressWarnings("deprecation") // major() has been replaced by feature() on 10, but wasn't present on 9
		int version = System.getProperty("java.version").startsWith("1.") 
				? 8 // may be lower, but then there's bigger problems here
				: Runtime.version().major();
		if (version < REQUIRED_VERSION) {
			String title = "Deimos Launcher";
			String message = "You are using an outdated version of Java (" + version + "), but you need at least " + REQUIRED_VERSION + " to run Deimos.\n"
					+ "Open a browser to download a newer Java release from https://adoptium.net/temurin/releases/?";
			int option = JOptionPane.showOptionDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, null, null);
			if (option == JOptionPane.YES_OPTION) {
				try {
					Desktop.getDesktop().browse(URI.create("https://adoptium.net/temurin/releases/"));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.exit(1);
		}
	}
}
