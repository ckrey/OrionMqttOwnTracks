/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package general;

import java.util.Timer;
import java.util.TimerTask;
import com.cinterion.misc.Watchdog;

/**
 *
 * @author christoph
 */
public class WatchDogTask extends TimerTask {
    final private int periodSec = 29;
    final private int holdSec = 2;
    final private int timeoutSec = 90;

    final private Timer timer;

    public boolean gpsRunning;
    public boolean GPRSRunning;

    public WatchDogTask() {
        ATManager.getInstance().executeCommandSynchron("at^scpin=1,5,1,0\r");
        Watchdog.start(timeoutSec);
        timer = new Timer();
        timer.scheduleAtFixedRate(this, 0, periodSec * 1000);
    }
    
    public void stop() {
        timer.cancel();
        Watchdog.start(0);
    } 
            
    public void run() {
            if (Settings.getInstance().getSetting("timerDebug", false)) {
                System.out.println("WatchDogTask " + System.currentTimeMillis());
            }

            if (gpsRunning && GPRSRunning) {
                if (Settings.getInstance().getSetting("timerDebug", false)) {
                    System.out.println("WatchDogTask will kick");
                }

                gpsRunning = false;
                GPRSRunning = false;
                
                Watchdog.kick();

                try {
                    ATManager.getInstance().executeCommandSynchron("at^ssio=5,1\r");
                    Thread.sleep(holdSec * 1000);
                    ATManager.getInstance().executeCommandSynchron("at^ssio=5,0\r");

                    ATManager.getInstance().executeCommandSynchron("at^ssio=5,1\r");
                    Thread.sleep(holdSec * 1000);
                    ATManager.getInstance().executeCommandSynchron("at^ssio=5,0\r");
                } catch (InterruptedException ie) {
                    //
                }
            }
    }
}
