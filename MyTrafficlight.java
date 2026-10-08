//These three lines bring in the SwiftBot library
//— the code that lets your program actually talk to the robot. 
//Without these, Java wouldn't know what a SwiftBot is.
import swiftbot.*;
import java.awt.image.BufferedImage;
//BufferedImage is Java's built-in way of storing a photo in memory as a grid of pixels. 
//When the SwiftBot takes a photo,
//it comes back as a BufferedImage.
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
//These are for getting the current date and time and formatting it nicely 
//(e.g. 2026-03-08 14:30:00).
import java.util.ArrayList;
//ArrayList = a resizable list (like a dynamic array)
//HashMap = a key-value store (like a dictionary — e.g. "RED" → 4)
//List and Map = the general types that ArrayList and HashMap belong to
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Traffic light Assignment 3
 * This program makes the SwiftBot navigate using colour-coded traffic light
 * detected by its on board camera. It responds to red, green, and blue signals
 */


public class MyTrafficlight {
	
	//This declares the class — the main container for all the code. 
	//Everything else in the file lives inside this one class. In Java, every program must have at least one class, and its name must match the filename
	//(TrafficLightController.java).
	
	
// Here are the constants	

    private static final int INITIAL_SPEED = 40;
    private static final int PASS_SPEED = 70;
    private static final int DETECTION_DISTANCE = 60;
    private static final int IMAGE_INTERVAL_MS = 500;
    private static final int STOP_DURATION_MS = 1000;
    private static final int PASS_DURATION_MS = 2000;
    private static final int BLUE_MOVE_MS = 2000;
    private static final int BLUE_TURN_DEGREES = 90;
    
    
    

  //INITIAL_SPEED = 40 → the robot drives at 40% of its maximum speed normally
  //PASS_SPEED = 60 → when passing a green light, it speeds up to 60%
  //DETECTION_DISTANCE = 30 → if a traffic light is 30cm or closer, the robot reacts
  //IMAGE_INTERVAL_MS = 500 → every 500 milliseconds (half a second), take a photo
  //STOP_DURATION_MS = 1000 → the robot waits for 1 full second when it needs to pause
  //PASS_DURATION_MS = 2000 → the robot has 2 seconds to pass a green light
  //BLUE_MOVE_MS = 2000 → during the blue light manoeuvre, it moves sideways for 1 second
  //BLUE_TURN_DEGREES = 90 → when doing the blue manoeuvre, it turns exactly 90 degrees
    
    
    // Colour names
    //private = only this class can use them
    //static = they belong to the class itself, not to any specific object
    //final = the value is locked and can never be reassigned
    
    
    private static final String RED = "Red";
    private static final String GREEN = "Green";
    private static final String BLUE = "Blue";
    private static final String NONE= "None";

    

    
    
    private final SwiftBotAPI swiftBot;
    private volatile boolean running = false;
    private volatile boolean terminateRequested = false;
    private volatile boolean continuePressed = false;
    
    //running = starts as un active; becomes active when Button A is pressed to start
    //terminateRequested = becomes active when Button X is pressed to stop everything
    //continuePressed = used at prompts — becomes activated when the user presses any button to confirm
    

  
    private int lightsDetectedCount = 0;
    //Every time a traffic light is detected this goes up by 1 
    
    private long startTimeMs;
    //This stores the time when the program has started

    // Log of all detected lights: each entry = "colour"
    private final List<String>         detectionLog  = new ArrayList<>();
    private final List<String>         fullLog       = new ArrayList<>();
    private final Map<String, Integer> colourCounts  = new HashMap<>();
    
    //detectionLog = a list of just the colour of each detected light, in order (e.g. ["RED", "GREEN", "BLUE", "RED"])
    //fullLog = a list of detailed human-readable log lines with timestamps (e.g. "[2026-03-08 14:30:00] Light #1 detected: RED at 28.0 cm")
    //colourCounts = a map from colour name to how many times it was seen (e.g. {"RED": 4, "GREEN": 6, "BLUE": 1})

    // Helper array for lambda capture (effectively final)
    private final boolean[] showLog2 = { false };

    // =========================================
    // Constructor
    // =========================================

   
    public MyTrafficlight() {
    	swiftBot = swiftbot.SwiftBotAPI.INSTANCE;   
    	colourCounts.put(RED, 0);
    	colourCounts.put(GREEN, 0);
    	colourCounts.put(BLUE, 0);
    	ButtonListeners();
    	
    }
    
    //The constructor is a special method that runs automatically when you create a new TrafficLightController()
    //SetupButtonListeners() — registers what should happen when buttons are pressed (explained next)
    
    // ========================================
    // Entry Point
    // ========================================

    
   
       	public static void main(String[] args) {
        	MyTrafficlight controller = new MyTrafficlight();
        	controller.MainMenuDisplay();
        	controller.StartButtonWait();
        	controller.runthegame();
        
        
        //Creates a TrafficLightController object (which runs the constructor above)
        //Shows the startup menu on screen
        //Waits (does nothing) until Button A is pressed
        //Starts the main navigation loop
    }

    // =========================================================
    // UI: Startup Menu
    // =========================================================

    private void MainMenuDisplay() {
        System.out.println("============================================================");
        System.out.println("            My Traffic Light Naviagtor!!!            ");
        System.out.println("============================================================");
        System.out.println("  Welcome! In this program your swiftbot will navugate through different colours that the Swiftbot Detects.");
        System.out.println("------------------------------------------------------------");
        System.out.println("  RED    --> Stop and wait 1 second, then resume");
        System.out.println("  GREEN  --> Pass the light within 2 seconds, then resume");
        System.out.println("  BLUE   --> Yield: divert left 90deg, return, then resume");
        System.out.println("------------------------------------------------------------");
        System.out.println("  [A] To Start Navigation Press Button A on the Swiftbot");
        System.out.println("  [X] To Terminate Navigation Press Button X on the Swiftbot");
        System.out.println("============================================================");
        System.out.println("  Waiting For Button A Input....");
    }

    //Here are the Button Listeners 

    private void ButtonListeners() {
        swiftBot.enableButton(Button.A, () -> {
            if (!running) {
                running = true;
            } else {
                continuePressed = true;
            }
        });
        
        // This tells the swiftBot "When Button A is physically pressed run this code"
        // If the program

        swiftBot.enableButton(Button.X, () -> {
            terminateRequested = true;
            continuePressed    = true;
        });
        //This tells the SwiftBot: "When Button A is physically pressed, run this code." The () -> { ... } part is called a lambda
        //— it's a short way of writing a small block of code that gets run later. Here:
        //If the program hasn't started yet (!running), pressing A sets running = true to start it
        //If the program is already running (e.g. at the 3-light prompt) 
        //pressing A sets continuePressed = true so the prompt loop unblocks

        swiftBot.enableButton(Button.Y, () -> {
            continuePressed = true;
        });
    }

    /**
     * Blocks until the user presses Button A to start.
     */
    
    private void StartButtonWait() {
    	while (!running) {
    		sleep(100);
    	}
    }

    // =======================================
    // Main Navigation Loop
    // =======================================

    private void runthegame() {
        startTimeMs = System.currentTimeMillis(); //Records the current time in milliseconds. This is used later to calculate how long the program ran.
        System.out.println("\n============================================================");
        System.out.println("  SYSTEM CONFIGURATION");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  Initial speed     : %d%%%n", INITIAL_SPEED);
        System.out.printf("  Detection range   : %d cm%n", DETECTION_DISTANCE);
        System.out.printf("  Image interval    : %d ms%n", IMAGE_INTERVAL_MS);
        System.out.println("------------------------------------------------------------");
        System.out.println("  Navigation STARTED  :)");
        System.out.println("============================================================\n");
        
        logLine ("Navigation started at " + currentTimestamp());
        // Set under lights to yellow and begin moving
        UnderLights(255, 165, 0);
        
        RobotMoveForward(INITIAL_SPEED);

        while (!terminateRequested) {
        	//This is the main loop — it keeps running as long as the user hasn't pressed X. 
        	//Inside, it first sleeps for 500ms (half a second) before doing anything, creating the image-capture interval.
            sleep(IMAGE_INTERVAL_MS);

            if (terminateRequested) break;

            // 1. Check distance sensor
            double distance = swiftBot.useUltrasound();
            //This reads the ultrasonic distance from the robot camera
            //Returns the distance to the nearest object in centimetres.
            // 2. If something is close, try to identify its colour
            
            if (distance <= DETECTION_DISTANCE) {
                BufferedImage image  = swiftBot.getQRImage();
                String colour =  TrafficLightColourFinder(image);
                
               
             //If something is within 30cm, the robot takes a photo and sends it to identifyTrafficLightColour() to work out what colour it is.

                if (!colour.equals(NONE)) {
                	MotorsStopping();
                    LightDetectionFound(colour, distance);

                    if (terminateRequested) break;

                    // After reacting the robot will resume forward
                   
                    UnderLights(255, 165, 0);
                    RobotMoveForward(INITIAL_SPEED);
                }
            }
        }

        MotorsStopping();
        UnderLights(0,0,0);
        TermiantionHandler();
    }

    // Here is the Traffic Light detection 
    
    private String TrafficLightColourFinder(BufferedImage image) {
        if (image == null) return NONE;
        
        long totalR = 0, totalG = 0, totalB = 0;
        int pixelCount = image.getWidth() * image.getHeight();
        
        for (int x = 0; x < image.getWidth(); x++) {
        	for (int y = 0; y < image.getHeight(); y++) {
        		int rgb = image.getRGB(x,y);
        		totalR += (rgb >> 16) & 0xFF;
        		totalG += (rgb >> 8) & 0xFF;
        		totalB += rgb  & 0xFF;
        	}
        }
       
        
        double avgR =(double) totalR / pixelCount;
        double avgG =(double) totalG / pixelCount;
        double avgB =(double) totalB / pixelCount;

        double minDominance = 50.0;

        //Checks whether one colour is dominant — specifically, whether it's at least 50 units higher than both other colours.
        //If red is dominant, return "RED". If green, return "GREEN". If blue, return "BLUE". If no colour dominates clearly, return "NONE" 
        //(meaning it wasn't a traffic light, perhaps just a wall or the floor).
        
        if (avgR > avgG + minDominance && avgR > avgB + minDominance) return RED;
        if (avgG > avgR + minDominance && avgG > avgB + minDominance) return GREEN;
        if (avgB > avgR + minDominance && avgB > avgG + minDominance) return BLUE;
        
        return NONE;
    }

    	//Here are the light responders

    private void LightDetectionFound(String colour, double distance) {
    	//Increments the counter, adds the colour to the detection list, and increments the count for that specific colour in the map. 
    	//getOrDefault(colour, 0) safely returns 0 if the colour somehow isn't in the map yet.
    	
        lightsDetectedCount++;
        detectionLog.add(colour);
        colourCounts.put(colour, colourCounts.getOrDefault(colour, 0) + 1);
        

        System.out.printf("LIGHT  DETECTED",  lightsDetectedCount);
        System.out.printf("  Colour   : %s%n",        colour);
        System.out.printf("  Distance : %.1f cm%n",   distance);
        //Increments the counter, adds the colour to the detection list, and increments the count for that specific colour in the map.
        //getOrDefault(colour, 0) safely returns 0 if the colour somehow isn't in the map yet.

        logLine(String.format("Light #%d detected: %s at %.1f cm",
                lightsDetectedCount, colour, distance));

        switch (colour) {
            case RED:   RedLightHandler();   break;
            case GREEN: GreenLightHandler(); break;
            case BLUE:  BlueLightHandler();  break;
            default:
                System.out.println("  [WARNING] Unknown colour - resuming.");
        }

        // After every 3 lights, ask user to continue or stop
        
        if (lightsDetectedCount % 3 == 0) {
        	ContinueOrTerminate();
        }
    }

    /**
     * Red light behaviour: stop, set under lights red, wait 1 second, resume.
     */
    private void RedLightHandler() {
        System.out.println("  [RED] Stopping at traffic light.");
        UnderLights(255, 0, 0);
        MotorsStopping();
        sleep(STOP_DURATION_MS);
        System.out.println("  [RED] Resuming at initial speed.");
        
        //Print a message saying the robot is stopping
        //Set under lights to red (255, 0, 0)
        //Stop the motors
        //Wait 1000ms (1 second)
        //Print that it's about to resume
    }

    /**
     * Green light behaviour: pass at speed for 2 seconds, then stop briefly.
     */
    private void GreenLightHandler() {
        System.out.println("  [GREEN] Passing traffic light.");
        UnderLights(0, 255, 0);
        RobotMoveForward(PASS_SPEED);
        sleep(PASS_DURATION_MS);
        MotorsStopping();
        System.out.println("  [GREEN] Passed. Stopping briefly.");
        sleep(STOP_DURATION_MS);
    }

    //Print a message
    //Set under lights to green (0, 255, 0)
    //Speed up to 60% to pass the light
    //Keep going for 2000ms (2 seconds) — enough time to physically pass it
    //Stop the motors
    //Wait 1000ms (1 second pause)
    
    
    private void BlueLightHandler() {
        System.out.println("  [BLUE] Yielding - diversion manoeuvre initiated.");
        MotorsStopping();
        sleep(STOP_DURATION_MS);
        
        //Stop and wait 1 second.
        // Blink under lights blue
        
        System.out.println("  [BLUE] Underlights: Blue (Blinking)");
        UnderLightsBlinking(0, 0, 255, 4, 300);

        System.out.println("  [BLUE] Turning 90 degrees left.");
        LeftTurn(BLUE_TURN_DEGREES);
        //Turn 90 degrees to the left

        // Move forward at low speed for 1 second
        System.out.println("  [BLUE] Moving forward at low speed for 1 second.");
        RobotMoveForward(INITIAL_SPEED);
        sleep(BLUE_MOVE_MS);
        MotorsStopping();

        // Wait 1 second before retracing
        sleep(STOP_DURATION_MS);

        // Retrace: turn right 90° to face original direction, move back same distance
        System.out.println("  [BLUE] Returning back to original path.");
        RightTurn(BLUE_TURN_DEGREES);
        //Turn right 90 degrees again to face the original forward direction
        RobotMoveForward(INITIAL_SPEED);
        sleep(BLUE_MOVE_MS);
        MotorsStopping();
        
        //Move forward at slow speed for 1 second, then stop. 
        //This is the "yield" movement 

        // Face forward on original path
        System.out.println("  [BLUE] Returned to original path. Resuming.");
        LeftTurn(BLUE_TURN_DEGREES);
    }

    // =========================================================
    // Prompt After Every 3 Lights
    // =========================================================

    private void ContinueOrTerminate() {
    	MotorsStopping();
        System.out.println("\n============================================================");
        System.out.printf("  Well done! You have successfully navigated %d traffic lights!%n",
                lightsDetectedCount);
        System.out.println("\n===========================================================");
        System.out.println("  [X] Press Button X to TERMINATE the program");
        System.out.println("  [Y] Press Button Y (or any button) to CONTINUE navigation");
        System.out.println("  Waiting for input...");
        System.out.println("============================================================");

        continuePressed = false;
        while (!continuePressed && !terminateRequested) {
            sleep(100);
        }
        //Resets continuePressed to false, then waits in a loop (checking every 100ms) until either a button is pressed (continuePressed becomes true) 
        //or X is pressed (terminateRequested becomes true). 
        //This is how the program pauses and waits for the user.

        if (!terminateRequested) {
            System.out.println("Continuing navigation.");
        }
    }

    // =========================================================
    // Termination and Log
    // =========================================================

    private void TermiantionHandler() {
       
    	
        long durationMs = System.currentTimeMillis() - startTimeMs;
        int	 minutes = (int) (durationMs / 60000);
        int  seconds = (int) ((durationMs % 60000) / 1000);
        
        //System.currentTimeMillis() - startTimeMs = total milliseconds elapsed
        // 60000 = convert to minutes (60000ms = 1 minute)
        //% 60000 / 1000 = the leftover seconds (e.g. if 150,000ms elapsed: 2 minutes and 30 seconds)

        System.out.println("\n============================================================");
        System.out.println("  Button X pressed - Initiating shutdown sequence...");
        System.out.println("------------------------------------------------------------");
        System.out.println("  Would you like to view the Execution Log?");
        System.out.println("  [Y] Press Button Y to DISPLAY log");
        System.out.println("  [X] Press Button X to SKIP and terminate");
        System.out.println("  Waiting for user input...");
        System.out.println("============================================================");

        continuePressed    = false;
        terminateRequested = false;
        showLog2[0]        = false;

        swiftBot.disableButton(Button.X);
        swiftBot.disableButton(Button.Y);
        
        // Re-register Y button to set showLog flag
        swiftBot.enableButton(Button.Y, () -> {
            showLog2[0]     = true;
            continuePressed = true;
        });
        
        // X button → skip log
        swiftBot.enableButton(Button.X, () -> {
            terminateRequested = true;
            continuePressed = true;
        });

        
        
        
        //Re-registers Y and X buttons with new behaviour specific to the log-display prompt: 
        //Y sets the "show log" flag to true; X just unblocks the wait.

        while (!continuePressed) {
        	 try {
                 Thread.sleep(50);
             } catch (InterruptedException e) {
                 e.printStackTrace();
             }
         }
        
        
        String mostFrequent = getMostFrequentColour();
        int    mostFreqCount = colourCounts.getOrDefault(mostFrequent, 0);

        if (showLog2[0]) {
        	ExecutionLogDisplay(durationMs, minutes, seconds, mostFrequent, mostFreqCount);
        } else {
            System.out.println("\n  Button X pressed - Skipping log display.");
        }

        // Write log to file regardless of user choice
        String filePath = TheLogFileWriter(durationMs, minutes, seconds, mostFrequent, mostFreqCount);
        System.out.println("\n============================================================");
        System.out.printf("  Log file saved to: %s%n", filePath);
        System.out.println("  Thank you for using SwiftBot Traffic Light Navigator! :)");
        System.out.println("============================================================");
        
        //Creates a filename like traffic_light_log_20260308_143000.txt. 
        //System.getProperty("user.dir") gets the folder the program is running from, so the file is saved in the same place.
    }   

    /**
     * Displays the execution log summary to the console.
     */
    private void ExecutionLogDisplay(long durationMs, int minutes, int seconds,
                                     String mostFrequent, int mostFreqCount) {
        System.out.println("\n============================================================");
        System.out.println("  Button Y pressed - Displaying Execution Log");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  Total lights encountered : %d%n",  lightsDetectedCount);
        System.out.printf("  Red lights               : %d%n",  colourCounts.get(RED));
        System.out.printf("  Green lights             : %d%n",  colourCounts.get(GREEN));
        System.out.printf("  Blue lights              : %d%n",  colourCounts.get(BLUE));
        System.out.println("------------------------------------------------------------");
        System.out.printf("  Most frequent colour     : %s (%d times)%n", mostFrequent, mostFreqCount);
        System.out.printf("  Total duration           : %d min %d sec%n",  minutes, seconds);
        System.out.println("============================================================");
    }

    /**
     * Writes the execution log to a time stamped text file.
     */
    private String TheLogFileWriter(long durationMs, int minutes, int seconds,
                                  String mostFrequent, int mostFreqCount) {
        String fileName = "traffic_light_log_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
        String filePath = System.getProperty("user.dir") + "/" + fileName;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("===== SwiftBot Traffic Light Navigator - Execution Log =====");
            writer.println("Generated: " + currentTimestamp());
            writer.println();
            writer.println("------------------------Summary --------------------------------");
            writer.printf("Total lights encountered : %d%n",  lightsDetectedCount);
            writer.printf("Red lights               : %d%n",  colourCounts.get(RED));
            writer.printf("Green lights             : %d%n",  colourCounts.get(GREEN));
            writer.printf("Blue lights              : %d%n",  colourCounts.get(BLUE));
            writer.printf("Most frequent colour     : %s (%d times)%n", mostFrequent, mostFreqCount);
            writer.printf("Total duration           : %d min %d sec (%d ms)%n",
                    minutes, seconds, durationMs);
            writer.println();
            writer.println("--- Detailed Detection Log ---");
            for (int i = 0; i < fullLog.size(); i++) {
                writer.printf("[%d] %s%n", i + 1, fullLog.get(i));
            }
        } catch (IOException e) {
            System.out.printf("  [ERROR] Could not write log file: %s%n", e.getMessage());
        }

        return filePath;
    }

    // =========================================================
    // SwiftBot Hardware Abstractions
    // =========================================================

    private void RobotMoveForward(int speedPercent) {
    	SpeedValidation(speedPercent);
        swiftBot.startMove(speedPercent, speedPercent);
    }
    //Calls validateSpeed first to check the value is valid, then calls swiftBot.startMove(left, right) with the same value for both wheels
    //— meaning it goes straight forward.

    private void MotorsStopping() {
        swiftBot.stopMove();
    }
    //Just calls the SwiftBot API to stop both motors.

    private void LeftTurn(int degrees) {
        long turnDurationMs = degrees * 10L;
        swiftBot.startMove(-30, 30);
        sleep(turnDurationMs);
        MotorsStopping();
    }
    //To turn left: run the left wheel backwards at -30% and the right wheel forwards at 30%. 

    
    private void RightTurn(int degrees) {
        long turnDurationMs = degrees * 10L;
        swiftBot.startMove(30, -30);
        sleep(turnDurationMs);
        MotorsStopping();
    }

    /**
     * Sets all under lights to the specified RGB colour.
     *
     *///
    
    private void UnderLights(int r, int g, int b) {
        int[] colour = { r, g, b };
        for (Underlight light : Underlight.values()) {
            swiftBot.setUnderlight(light, colour);
            
            //Creates an RGB array and loops through every under light on the robot
            //(the SwiftBot has multiple LEDs underneath)
        }
    }

    private void UnderLightsBlinking(int r, int g, int b, int times, long intervalMs) {
        for (int i = 0; i < times; i++) {
        	UnderLights(r, g, b);
            sleep(intervalMs);
            UnderLights(0, 0, 0);
            sleep(intervalMs);
            //Loops times times. Each loop: turn lights on → wait → turn lights off → wait. 
            //This creates the blinking effect.
        }
    }

    // =========================================================
    // Validation
    // =========================================================

    
    private void SpeedValidation(int speedPercent) {
        if (speedPercent < 0 || speedPercent > 100) {
            throw new IllegalArgumentException(
                    "Speed must be between 0 and 100, but was: " + speedPercent);
          //Checks the speed is between 0 and 100.
          //If not, it throws an exception — an error that immediately stops the program and prints a message
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private String getMostFrequentColour() {
        String most = RED;
        for (Map.Entry<String, Integer> entry : colourCounts.entrySet()) {
            if (entry.getValue() > colourCounts.get(most)) {
                most = entry.getKey();
            }
        }
        return most;
        //Starts by assuming RED is the most common.
        //Then loops through all entries in the colourCounts map.
    }

    private void logLine(String message) {
        fullLog.add("[" + currentTimestamp() + "] " + message);
    }

    private String currentTimestamp() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
 
