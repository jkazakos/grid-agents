import jason.JasonException;
import jason.infra.local.RunLocalMAS;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        // 1. Fix Linux Swing blank rendering bug by applying Nimbus Look and Feel
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }

        // Enable clean font rendering on Linux
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // 2. Preserve true terminal output streams
        PrintStream terminalOut = System.out;
        PrintStream terminalErr = System.err;

        try {
            String[] masArgs = (args != null && args.length > 0) ? args : new String[] { "grid_agent.mas2j" };

            // 3. Initialize MAS project
            RunLocalMAS runner = new RunLocalMAS();
            runner.init(masArgs);
            runner.create();

            // 4. Properly size and repaint the MASConsoleGUI window so it renders cleanly
            try {
                Class<?> consoleClass = Class.forName("jason.runtime.MASConsoleGUI");
                Method getMethod = consoleClass.getMethod("get");
                Object consoleInstance = getMethod.invoke(null);
                Method getFrameMethod = consoleClass.getMethod("getFrame");
                JFrame consoleFrame = (JFrame) getFrameMethod.invoke(consoleInstance);

                if (consoleFrame != null) {
                    SwingUtilities.invokeLater(() -> {
                        consoleFrame.setTitle("Jason MAS Console");
                        consoleFrame.setSize(750, 550);
                        consoleFrame.setLocation(100, 100);
                        consoleFrame.setVisible(true);
                        consoleFrame.revalidate();
                        consoleFrame.repaint();
                    });
                }
            } catch (Throwable ignored) {
            }

            // 5. Tee output: send all logs to BOTH Terminal AND MASConsole window
            PrintStream masOut = System.out;
            PrintStream teeOut = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    terminalOut.write(b);
                    if (masOut != null && masOut != terminalOut) {
                        try {
                            masOut.write(b);
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    terminalOut.write(b, off, len);
                    if (masOut != null && masOut != terminalOut) {
                        try {
                            masOut.write(b, off, len);
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void flush() {
                    terminalOut.flush();
                    if (masOut != null && masOut != terminalOut) {
                        try {
                            masOut.flush();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }, true);

            System.setOut(teeOut);
            System.setErr(terminalErr);

            terminalOut.println("=================================================================");
            terminalOut.println("           JASON MULTI-AGENT SYSTEM (MAS) INITIALIZED            ");
            terminalOut.println("   Grid World: 5x5 Matrix  |  Autonomous BDI Rational Agents     ");
            terminalOut.println("   Console Output Streaming to Terminal & Swing GUI              ");
            terminalOut.println("=================================================================");

            // 6. Start agents
            runner.start();
            runner.waitEnd();
        } catch (JasonException e) {
            terminalErr.println("JasonException: " + e.getMessage());
            e.printStackTrace(terminalErr);
        } catch (Exception e) {
            terminalErr.println("Exception: " + e.getMessage());
            e.printStackTrace(terminalErr);
        }
    }
}
