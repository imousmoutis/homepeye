import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;

public class Main{
	
	static MqttClient client;
	static MqttConnectOptions connOpt;
	
	static boolean system_on = false;
	static boolean breach_notification_delivered;
	
	// create gpio controller
    final static GpioController gpio = GpioFactory.getInstance();
    
    //gpio 17 
    final static GpioPinDigitalOutput system_on_led = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, PinState.LOW);
    
    //gpio 22
    final static GpioPinDigitalInput pir_motion_detector = gpio.provisionDigitalInputPin(RaspiPin.GPIO_03, PinPullResistance.PULL_DOWN);
    
    //gpio 27
    final static GpioPinDigitalInput reed_switch_detector = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02, PinPullResistance.PULL_UP);
	
    //gpio 18
    final static GpioPinDigitalOutput buzzer = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, PinState.LOW);
    
	static EmailServer emailServer = new EmailServer();
	     
    private static void connectToMqtt(){

    	try{
			
			client = new MqttClient("tcp://iot.eclipse.org:1883", "home-peye_server+" +System.currentTimeMillis(), new MemoryPersistence());
			
			connOpt = new MqttConnectOptions();
			
			connOpt.setCleanSession(true);
	
			client.connect(connOpt);
	        
			client.subscribe("/home-peye/server/#", 1);

	        client.setCallback(new MqttCallback() {
	            
	            public void connectionLost(Throwable cause) { 
	            	
	            	connectToMqtt();
	            }

	            public void messageArrived(String topic, MqttMessage msg) throws Exception {
	                
	            	if (topic.equals("/home-peye/server/status/id/"))
	            		sendMessage("/home-peye/client/status/id/", "" +system_on);
	            	else if (topic.equals("/home-peye/server/status/")){
	            		
	            		String state = new String(msg.getPayload());
	            		
						if (state.equals("true")) {

							system_on_led.high();
							system_on = true;
							
							breach_notification_delivered = false;
							
							System.out.println("System is on.");

							new Thread() {

								public void run() {

									while (system_on) {
										
										if((pir_motion_detector.getState() == PinState.HIGH) || (reed_switch_detector.getState() == PinState.HIGH)){

											System.out.println("Hmmm something is going on.");
											
											buzzer.high();
											
											try {		
												
												Process p = Runtime.getRuntime().exec("fswebcam -r 1280x1024 -S 20 pic.jpg");
												p.waitFor();
												
											    BufferedImage image = ImageIO.read(new File("pic.jpg"));
											      
											    String base64String = imgToBase64String(image, "png");
												
											    System.out.println("Publishing the image.");
											    
												sendMessage("/home-peye/client/breach/", base64String);
											
												Thread.sleep(10000);
												
												if (!breach_notification_delivered){
													
													System.out.println("Sending an email notification.");
													 
											    	emailServer.sendEmail();
											    	
											    	breach_notification_delivered = true;
								            		system_on_led.low();
								            		system_on = false;
												}
												
											} catch (InterruptedException e) {
												e.printStackTrace();
											} catch (IOException e) {
												e.printStackTrace();
											}
										}
									}
								}
							}.start();

						} else{
	            			
	            			system_on_led.low();
	            			system_on = false;
	            			
	            			System.out.println("System is off.");
	            		}
	            		sendMessage("/home-peye/client/status/id/", "" +system_on);
	            	}else if (topic.equals("/home-peye/server/breach/")){
	            		
	            		System.out.println("User has been notified.");
	            		
	            		breach_notification_delivered = true;
	            		system_on_led.low();
	            		system_on = false;
	            	}else if (topic.equals("/home-peye/server/sound/"))
	            		buzzer.low();
	            	else if (topic.equals("/home-peye/server/stream/")){
	            		
	            		String state = new String(msg.getPayload());
	            		
						if (state.equals("true")){
							Process p = Runtime.getRuntime().exec("sudo /etc/init.d/motion restart");
							p.waitFor();
							
							sendMessage("/home-peye/client/stream/", "");
						}else
							Runtime.getRuntime().exec("sudo /etc/init.d/motion stop");
							
	            	}
	            		

	            }
	            	            
	            public void sendMessage(String topic, String content){
	            	
	            	MqttMessage message = new MqttMessage(content.getBytes());
	            	MqttTopic topicToSend = client.getTopic(topic);
	           
	            	try {
	            		
	            		topicToSend.publish(message);
	        		} catch (MqttPersistenceException e) {
	        			e.printStackTrace();
	        		} catch (MqttException e) {
	        			e.printStackTrace();
	        		}	
	            }

	            public void deliveryComplete(IMqttDeliveryToken token) {
	            	
	            	//do nothing
	            }
	        });
	    }
	    catch(MqttException e){

	    }
    };
    
	public static void main(String[] args) {
				
		connectToMqtt();
	};
	
	public static String imgToBase64String(final RenderedImage img, final String formatName) {
	    final ByteArrayOutputStream os = new ByteArrayOutputStream();
	    try {
	        ImageIO.write(img, formatName, Base64.getEncoder().wrap(os));
	        return os.toString(StandardCharsets.ISO_8859_1.name());
	    } catch (final IOException ioe) {
	        throw new UncheckedIOException(ioe);
	    }
	};

}

