package ca.bc.gov.open.rsbc.mailit.mail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import ca.bc.gov.open.rsbc.mailit.mail.api.MailApiDelegate;
import ca.bc.gov.open.rsbc.mailit.mail.api.model.EmailObject;
import ca.bc.gov.open.rsbc.mailit.mail.api.model.EmailRequest;
import ca.bc.gov.open.rsbc.mailit.mail.api.model.EmailResponse;
import ca.bc.gov.open.rsbc.mailit.mail.mappers.SimpleMessageMapper;


@Service
public class MailApiDelegateImpl implements MailApiDelegate {

	private final JavaMailSender emailSender;

	private final SimpleMessageMapper simpleMessageMapper;

	Logger logger = LoggerFactory.getLogger(MailApiDelegateImpl.class);

	public MailApiDelegateImpl(JavaMailSender emailSender, SimpleMessageMapper simpleMessageMapper) {
		this.emailSender = emailSender;
		this.simpleMessageMapper = simpleMessageMapper;
	}

	@Override
	public ResponseEntity<EmailResponse> mailSend(EmailRequest emailRequest) {
		logger.info("Beginning mail send");

		Optional<EmailObject> emailObject = emailRequest.getTo().stream().findFirst();
		EmailResponse emailResponse = new EmailResponse();

		if (!emailObject.isPresent()) {
			logger.error("No value present in email object");
			return new ResponseEntity("error", HttpStatus.BAD_REQUEST);
		}

		// No attachmentment(s)
		if (null == emailRequest.getAttachment() || emailRequest.getAttachment().size() == 0) {

			logger.info("Mapping message");
			SimpleMailMessage simpleMailMessage = simpleMessageMapper.toSimpleMailMessage(emailRequest);

			logger.info("Sending message");
			emailSender.send(simpleMailMessage);

			// EmailResponse emailResponse = new EmailResponse();
			emailResponse.setAcknowledge(true);

			logger.info("Message sent successfully w/o attachment(s)");
			return ResponseEntity.accepted().body(emailResponse);

			// Has attachments
		} else {
			
			MimeMessage message = emailSender.createMimeMessage();
			
			try {
				
				MimeMessageHelper helper = new MimeMessageHelper(message, true);
			
				helper.setFrom(emailRequest.getFrom().getEmail());
				
				// Extract the to(s). 
				List<String> tos = new ArrayList<String>();
				for (EmailObject element: emailRequest.getTo()) {
					tos.add(element.getEmail());
				}			
			    helper.setTo(tos.toArray(new String[0]));
			    
			    helper.setSubject(emailRequest.getSubject());
			    
			    if (emailRequest.getContent().getType().equalsIgnoreCase("text/plain")) {
			    	helper.setText(emailRequest.getContent().getValue(), false); 
			    } else if (emailRequest.getContent().getType().equalsIgnoreCase("text/html")) { 
			    	helper.setText(emailRequest.getContent().getValue(), true); 
			    } else {
			    	logger.error("Invalid or missing content type value");
					return new ResponseEntity("error", HttpStatus.BAD_REQUEST);
			    }
			    
			    // Load the attachment to a temp file  
			    logger.info(new String());
		        byte[] attachment = emailRequest.getAttachment().get(0).getFilecontents();
			    Path tempFile = Files.createTempFile(null, null);
			    Files.write(tempFile, attachment);
	
			    // TODO - Add the attachment - This should be updated to support multiple attached files. The yaml file is already defined 
			    // the attachment objects as an array. (see jag-mail-it-api.yaml)
			    helper.addAttachment(emailRequest.getAttachment().get(0).getFilename(), tempFile.toFile());
	
			    emailSender.send(message);	
	
				emailResponse.setAcknowledge(true);
				
				// Delete the attachment
				boolean isDelete = tempFile.toFile().delete();
				logger.info("Temp file was deleted? " + isDelete);
	
				logger.info("Message sent successfully w/attachment(s)");
				return ResponseEntity.accepted().body(emailResponse);
			
			
			} catch (MessagingException | IOException e) { 
				e.printStackTrace();
				return new ResponseEntity("error", HttpStatus.BAD_REQUEST);
			} 
		}
	}
}
