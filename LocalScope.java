import java.io.InputStream;
import java.io.OutputStream;
import gnu.io.CommPortIdentifier; 
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent; 
import gnu.io.SerialPortEventListener; 
import java.util.Enumeration;
// for the Frame:
import javax.swing.*;
// for the Canvas
import java.awt.*;
import java.awt.image.BufferStrategy;
import javax.swing.Timer;
// for the Chrono
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class LocalScope implements SerialPortEventListener {
	SerialPort serialPort;
        /** The port we're normally going to use. */
	private static final String PORT_NAMES[] = { 
			"/dev/tty.usbserial-A9007UX1", // Mac OS X
			"/dev/ttyUSB0", // Linux
			"COM3", // Windows
			};
	/** Buffered input stream from the port */
	private InputStream input;
	/** The output stream to the port */
	private OutputStream output;
	/** Milliseconds to block while waiting for port open */
	private static final int TIME_OUT = 2000;
	/** Default bits per second for COM port. -- default was 9600 */
//	private static final int DATA_RATE = 57600;
	private static final int DATA_RATE = 115200;  // Peter Malcolm: for oscilloscope (PM)

        // the height of the oscilloscope
        public static final int SCOPE_HEIGHT = 256;
        
        // used elsewhere to determine width of oscilloscope
//        public static final int SCOPE_WIDTH = 1280;
        public static final int SCOPE_WIDTH = 1200;
        
        public byte[] values = new byte[SCOPE_WIDTH];
        
        private int currentPosition = 0;
        
        private LocalFrame myLFrame = null;
        
	public void initialize() {
            
                System.out.println("Created GUI on EDT? " + SwingUtilities.isEventDispatchThread());
                LocalScope l = new LocalScope();
                
                // set up empty array of values:
                for(int i = 0; i < SCOPE_WIDTH; i++) {
                    values[i] = 0;
                }

                l.myLFrame = new LocalFrame(this);                              // call constructor of LocalFrame
                
		CommPortIdentifier portId = null;
		Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();

		// iterate through, looking for the port
		while (portEnum.hasMoreElements()) {
			CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
			for (String portName : PORT_NAMES) {
				if (currPortId.getName().equals(portName)) {
					portId = currPortId;
					break;
				}
			}
		}

		if (portId == null) {
			System.out.println("Could not find COM port.");
			return;
		}

		try {
			// open serial port, and use class name for the appName.
			serialPort = (SerialPort) portId.open(this.getClass().getName(),
					TIME_OUT);

			// set port parameters
			serialPort.setSerialPortParams(DATA_RATE,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);

			// open the streams
			input = serialPort.getInputStream();
			output = serialPort.getOutputStream();

			// add event listeners
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
		} catch (Exception e) {
			System.err.println(e.toString());
		}
	}

	/**
	 * This should be called when you stop using the port.
	 * This will prevent port locking on platforms like Linux.
	 */
	public synchronized void close() {
		if (serialPort != null) {
			serialPort.removeEventListener();
			serialPort.close();
		}
	}

	/**
	 * Handle an event on the serial port. Read the data and print it.
	 */
	public synchronized void serialEvent(SerialPortEvent oEvent) {
		if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				int available = input.available();
				byte chunk[] = new byte[available];
				input.read(chunk, 0, available);

				// Displayed results are codepage dependent
				// System.out.print(new String(chunk)); //  print this (PM)
                                
//				System.out.print(Integer.toHexString(0xFF & chunk[0])); //  print this (PM)
//				System.out.print((int)chunk[0]); //  print this (PM)
//				System.out.print(" [");                         //  print [ (PM)
//                                System.out.print(available);                    //  print available bytes
//				System.out.print("] ");                         //  print ] (PM)
                                
                                for(int i = 0; i < available; i++) {
                                    values[currentPosition] = chunk[i];         // put the data in the array
                                    currentPosition++;                              // increment the pointer
                                    if(currentPosition >= SCOPE_WIDTH) {            // wrap if necessary
                                        currentPosition = 0;
                                    }
                                }
                                

			} catch (Exception e) {
				System.err.println(e.toString());
			}
		}
		// Ignore all the other eventTypes, but you should consider the other ones.
	}

	public static void main(String[] args) throws Exception {
		LocalScope main = new LocalScope();
		main.initialize();
		System.out.println("Started");
	}
}

class LocalFrame extends JFrame {
	LocalFrame(LocalScope lScope) {
 		// frame description
		super("Arduino Buffered Oscilloscope");
		// our Canvas
		LocalCanvas canvas = new LocalCanvas(lScope);
		add(canvas, BorderLayout.CENTER);
		// set it's size and make it visible
		setSize(lScope.SCOPE_WIDTH, lScope.SCOPE_HEIGHT);
		setVisible(true);		
		// now that is visible we can tell it that we will use 2 buffers to do the repaint
		// befor being able to do that, the Canvas as to be visible
		canvas.createBufferStrategy(2);
                canvas.computationDone = true;
	}
	// just to start the application
        // THIS IS ACTUALLY NEVER USED.  LOCALFRAME IS CREATED BY LOCALSCOPE
        /*
	public static void main(String[] args) {
		// instance of our stuff
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new LocalFrame();
			}
		});
	}
         * 
         */
}

// OK this the class where we will draw and where computations
// will be done in another thread
/* class LocalCanvas extends Canvas implements Runnable { */
    
// PM: We do not need to implement Runnable for the canvas in this case.
// the LocalCanvas.myRepaint() method gets called from the Chrono,
// and I won't worry about time-consuming repainting for now.
class LocalCanvas extends Canvas {
	// back color LightYellow
	Color backColor = new Color(255, 255, 150);
	// my Swing timer
	Timer timer;
	// for the computation of the postion and the repaint
	Dimension size;
    
	int centerX, centerY;
	// a boolean to synchronize computation and drawing
	public boolean computationDone = false;
	// a boolean to ask the treat to stop
	boolean threadStop = false;
	
        LocalScope myLScopeGrandparent = null;
        
        byte[] myCopyOfData = null;
        
	// this is a Canvas but I wont't let the system when to repaint it I will do it myself
	LocalCanvas(LocalScope lScope) {            
		super();
                // grab a pointer to the LocalScope that will be collecting the data
                myLScopeGrandparent = lScope;
                
                myCopyOfData = new byte[lScope.SCOPE_WIDTH];
                // initialize an entire array of zeroes:
                for(int i = 0; i < lScope.SCOPE_WIDTH; i++) {
                    myCopyOfData[i] = 0;
                }
                        
		// so ignore System's paint request I will handle them
		setIgnoreRepaint(true);

		// build Chrono that will call me 
		Chrono chrono = new Chrono(this);
		// ask the chrono to calll me every 60 times a second so every 16 ms (set say 15 for the time it takes to paint)
		timer = new Timer(16, chrono);
		timer.start();
	}
	
	// my own paint method that repaint off line and switch the displayed buffer
	// according to the VBL
	public synchronized void myRepaint() {
		// computation a lot longer than expected (more than 15ms)... ignore it
		if(!computationDone) {
			return;
		}
                // System.out.println("size="+size);
                int theWidth = myLScopeGrandparent.SCOPE_WIDTH;     // alias width
                int theHeight = myLScopeGrandparent.SCOPE_HEIGHT;     // alias width

		// ok doing the repaint on the not showed page
		BufferStrategy strategy = getBufferStrategy();
                // System.out.println(strategy);
		Graphics graphics = strategy.getDrawGraphics();
		// erase all what I had
		graphics.setColor(backColor);	
		graphics.fillRect(0, 0, theWidth, theHeight);
                // draw the line across the center of the scope
		graphics.setColor(Color.BLACK);	
                graphics.drawLine(0, (theHeight/2), theWidth, (theHeight/2));
                
                
                
                // CODE HERE TO COPY OVER A LOCAL VERSION OF THE CURRENT LOCAL-CANVAS DATA STATE
                for(int i = 0; i < theWidth; i++) { 
                    myCopyOfData[i] = myLScopeGrandparent.values[i];
                    // System.out.println(myCopyOfData[i]); // all zeroes...?
                }
                 
                // CODE HERE TO REDRAW ALL SCOPE LINES
                // RECOMMENDED:
                // LOOP THRU WIDTH OF CANVAS -- this will be the same as the size of the data-array
                // EACH TIME DRAWS A LINE ON GRAPHICS
                for(int i = 0; i < theWidth; i++) {
                    if(0==i){                                                   // wrap around at left side
                        graphics.drawLine(i, theHeight/2-(int)myCopyOfData[theWidth-1], i, theHeight/2-(int)myCopyOfData[0]);
                    } else {
                        graphics.drawLine(i, theHeight/2-(int)myCopyOfData[i-1], i, theHeight/2-(int)myCopyOfData[i]);
                    }
                }
                
		if(graphics != null)
			graphics.dispose();
		// show next buffer
		strategy.show();
		// synchronized the blitter page shown
		Toolkit.getDefaultToolkit().sync();
		// ok I can be called again
		// computationDone = false;
	}
}

/** Will be called at each blitter page */
class Chrono implements ActionListener {

	LocalCanvas lc;
	// constructor that receives the GameCanvas that we will repaint every 60 milliseconds
	Chrono(LocalCanvas lc) {
		this.lc = lc;
	}
	// calls the method to repaint the anim everytime I am called
	public void actionPerformed(ActionEvent e) {
		lc.myRepaint();
	}

}
