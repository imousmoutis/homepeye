import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailServer {

	static Properties mailServerProperties;
	static Session getMailSession;
	static MimeMessage generateMailMessage;
	static Transport transport;
	
	public EmailServer() {
		
		mailServerProperties = System.getProperties();
		mailServerProperties.put("mail.smtp.port", "587");
		mailServerProperties.put("mail.smtp.auth", "true");
		mailServerProperties.put("mail.smtp.starttls.enable", "true");
		
		getMailSession = Session.getDefaultInstance(mailServerProperties, null);
		
		generateMailMessage = new MimeMessage(getMailSession);
		
		try {
			
			generateMailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress("receiver_alias"));
			generateMailMessage.setSubject("BREACH ALERT! [home-peye]");
			
			transport = getMailSession.getTransport("smtp");
			transport.connect("smtp.gmail.com", "alias@gmail.com", "password");
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}
	
	public void sendEmail(){
		
		try {
						
			MimeMultipart multipart = new MimeMultipart("related");
			
			//text
			BodyPart messageBodyPart = new MimeBodyPart();
	        String htmlText = "<h2>home-peye detected some movement.</h2>"
	        		+"<h3>Take a look: </h3><img src=\"cid:image\">";
	        messageBodyPart.setContent(htmlText, "text/html");
	        multipart.addBodyPart(messageBodyPart);
	        
	        //image
	        messageBodyPart = new MimeBodyPart();
	        DataSource fds = new FileDataSource("pic.jpg");
	        messageBodyPart.setDataHandler(new DataHandler(fds));
	        messageBodyPart.setHeader("Content-ID", "<image>");
	        multipart.addBodyPart(messageBodyPart);
			
			generateMailMessage.setContent(multipart);
			
			transport.sendMessage(generateMailMessage, generateMailMessage.getAllRecipients());
			transport.close();
		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}

}
