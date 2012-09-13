import java.awt.BorderLayout;
import javax.swing.*;


public class GameFrame extends JFrame {
	GameFrame() {
		// frame description
		super("Bouncing ball");
		// our Canvas
		GameCanvas canvas = new GameCanvas();
		add(canvas, BorderLayout.CENTER);
		// set it's size and make it visible
		setSize(600, 400);
		setVisible(true);		
		// now that is visible we can tell it that we will use 2 buffers to do the repaint
		// befor being able to do that, the Canvas as to be visible
		canvas.createBufferStrategy(2);
	}
	// just to start the application
	public static void main(String[] args) {
		// instance of our stuff
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new GameFrame();
			}
		});
	}
}

