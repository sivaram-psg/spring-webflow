package org.springframework.faces.webflow;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import org.springframework.binding.message.Message;
import org.springframework.binding.message.MessageCriteria;
import org.springframework.binding.message.MessageResolver;
import org.springframework.binding.message.Severity;
import org.springframework.context.MessageSource;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.StringUtils;
import org.springframework.webflow.execution.RequestContext;

/**
 * Helper delegate class for use with the {@link FlowFacesContext} that handles all faces message methods.
 * 
 * @author Jeremy Grelle
 * @author Phillip Webb
 */
public class FlowFacesContextMessageDelegate {

	private RequestContext context;

	/**
	 * Key for identifying summary messages
	 */
	static final String SUMMARY_MESSAGE_KEY = "_summary";

	/**
	 * Key for identifying detail messages
	 */
	static final String DETAIL_MESSAGE_KEY = "_detail";

	/**
	 * Mappings between {@link FacesMessage} and {@link Severity}.
	 */
	private static final Map<FacesMessage.Severity, Severity> FACESSEVERITY_TO_SPRINGSEVERITY;
	static {
		FACESSEVERITY_TO_SPRINGSEVERITY = new HashMap<FacesMessage.Severity, Severity>();
		FACESSEVERITY_TO_SPRINGSEVERITY.put(FacesMessage.SEVERITY_INFO, Severity.INFO);
		FACESSEVERITY_TO_SPRINGSEVERITY.put(FacesMessage.SEVERITY_WARN, Severity.WARNING);
		FACESSEVERITY_TO_SPRINGSEVERITY.put(FacesMessage.SEVERITY_ERROR, Severity.ERROR);
	}

	public FlowFacesContextMessageDelegate(RequestContext context) {
		super();
		this.context = context;
	}

	/**
	 * Whether Web Flow's MessageContext contains any errors.
	 */
	public boolean hasErrorMessages() {
		return context.getMessageContext().hasErrorMessages();
	}

	/**
	 * @see FlowFacesContext#addMessage(String, FacesMessage)
	 */
	public void addToFlowMessageContext(String clientId, FacesMessage message) {
		String source = null;
		if (StringUtils.hasText(clientId)) {
			source = clientId;
		}
		context.getMessageContext().addMessage(new FlowFacesMessageAdapter(source, SUMMARY_MESSAGE_KEY, message));
		context.getMessageContext().addMessage(new FlowFacesMessageAdapter(source, DETAIL_MESSAGE_KEY, message));
	}

	/**
	 * @see FlowFacesContext#getClientIdsWithMessages
	 */
	public Iterator<String> getClientIdsWithMessages() {
		return new ClientIdIterator();
	}

	/**
	 * @see FlowFacesContext#getMaximumSeverity()
	 */
	public FacesMessage.Severity getMaximumSeverity() {
		if (context.getMessageContext().getAllMessages().length == 0) {
			return null;
		}
		FacesMessage.Severity max = FacesMessage.SEVERITY_INFO;
		Iterator<FacesMessage> messages = getMessages();
		while (messages.hasNext()) {
			FacesMessage message = messages.next();
			if (message.getSeverity().getOrdinal() > max.getOrdinal()) {
				max = message.getSeverity();
			}
			if (max.getOrdinal() == FacesMessage.SEVERITY_FATAL.getOrdinal()) {
				break;
			}
		}
		return max;
	}

	/**
	 * @see FlowFacesContext#getMessages()
	 */
	public Iterator<FacesMessage> getMessages() {
		return new FacesMessageIterator();
	}

	/**
	 * @see FlowFacesContext#getMessages(String)
	 */
	public Iterator<FacesMessage> getMessages(String clientId) {
		return new FacesMessageIterator(clientId);
	}

	/**
	 * @see Jsf2FlowFacesContext#getMessageList()
	 */
	public List<FacesMessage> getMessageList() {
		return new FacesMessageIterator().asList();
	}

	/**
	 * @see Jsf2FlowFacesContext#getMessageList(String)
	 */
	public List<FacesMessage> getMessageList(String clientId) {
		return new FacesMessageIterator(clientId).asList();
	}

	// ------------------ Private helper methods ----------------------//

	private FacesMessage toFacesMessage(Message summaryMessage, Message detailMessage) {

		// If we can return the actual message instance.
		if (summaryMessage instanceof FlowFacesMessageAdapter) {
			return ((FlowFacesMessageAdapter) summaryMessage).getFacesMessage();
		}
		if (detailMessage instanceof FlowFacesMessageAdapter) {
			return ((FlowFacesMessageAdapter) detailMessage).getFacesMessage();
		}

		// If we have not got an actual instance adapt the message
		if (summaryMessage.getSeverity() == Severity.INFO) {
			return new FacesMessage(FacesMessage.SEVERITY_INFO, summaryMessage.getText(), detailMessage.getText());
		} else if (summaryMessage.getSeverity() == Severity.WARNING) {
			return new FacesMessage(FacesMessage.SEVERITY_WARN, summaryMessage.getText(), detailMessage.getText());
		} else if (summaryMessage.getSeverity() == Severity.ERROR) {
			return new FacesMessage(FacesMessage.SEVERITY_ERROR, summaryMessage.getText(), detailMessage.getText());
		} else {
			return new FacesMessage(FacesMessage.SEVERITY_FATAL, summaryMessage.getText(), detailMessage.getText());
		}
	}

	private class FacesMessageIterator implements Iterator<FacesMessage> {

		private List<FacesMessage> messages;

		private int currentIndex = -1;

		protected FacesMessageIterator() {
			Message[] summaryMessages = context.getMessageContext().getMessagesByCriteria(new SummaryMessageCriteria());
			Message[] detailMessages = context.getMessageContext().getMessagesByCriteria(new DetailMessageCriteria());
			Message[] userMessages = context.getMessageContext().getMessagesByCriteria(new UserMessageCriteria());

			messages = new ArrayList<FacesMessage>();
			for (int i = 0; i < summaryMessages.length; i++) {
				messages.add(toFacesMessage(summaryMessages[i], detailMessages[i]));
			}
			for (Message userMessage : userMessages) {
				messages.add(toFacesMessage(userMessage, userMessage));
			}
		}

		protected FacesMessageIterator(String clientId) {
			Message[] summaryMessages = context.getMessageContext().getMessagesBySource(clientId + SUMMARY_MESSAGE_KEY);
			Message[] detailMessages = context.getMessageContext().getMessagesBySource(clientId + DETAIL_MESSAGE_KEY);
			Message[] userMessages = context.getMessageContext().getMessagesBySource(clientId);

			this.messages = new ArrayList<FacesMessage>();
			for (int i = 0; i < summaryMessages.length; i++) {
				messages.add(toFacesMessage(summaryMessages[i], detailMessages[i]));
			}
			for (Message userMessage : userMessages) {
				messages.add(toFacesMessage(userMessage, userMessage));
			}
		}

		public boolean hasNext() {
			return messages.size() > currentIndex + 1;
		}

		public FacesMessage next() {
			currentIndex++;
			return messages.get(currentIndex);
		}

		public void remove() {
			throw new UnsupportedOperationException("Messages cannot be removed through this iterator.");
		}

		public List<FacesMessage> asList() {
			if (null == messages) {
				return Collections.unmodifiableList(Collections.<FacesMessage> emptyList());
			} else {
				return Collections.unmodifiableList(messages);
			}
		}

	}

	private class ClientIdIterator implements Iterator<String> {

		private Message[] messages;

		int currentIndex = -1;

		protected ClientIdIterator() {
			this.messages = context.getMessageContext().getMessagesByCriteria(new IdentifiedMessageCriteria());
		}

		public boolean hasNext() {
			return messages.length > currentIndex + 1;
		}

		public String next() {
			Message next = messages[++currentIndex];
			if (next.getSource() == null) {
				return null;
			} else if (next.getSource().toString().endsWith(SUMMARY_MESSAGE_KEY)) {
				return next.getSource().toString().replaceAll(SUMMARY_MESSAGE_KEY, "");
			} else {
				return next.getSource().toString();
			}
		}

		public void remove() {
			throw new UnsupportedOperationException("Messages cannot be removed through this iterator.");
		}

	}

	private class SummaryMessageCriteria implements MessageCriteria {

		public boolean test(Message message) {
			if (message.getSource() == null) {
				return false;
			}
			return message.getSource().toString().endsWith(SUMMARY_MESSAGE_KEY);
		}
	}

	private class DetailMessageCriteria implements MessageCriteria {

		public boolean test(Message message) {
			if (message.getSource() == null) {
				return false;
			}
			return message.getSource().toString().endsWith(DETAIL_MESSAGE_KEY);
		}
	}

	private class UserMessageCriteria implements MessageCriteria {

		public boolean test(Message message) {
			if (message.getSource() == null) {
				return true;
			}
			return !message.getSource().toString().endsWith(SUMMARY_MESSAGE_KEY)
					&& !message.getSource().toString().endsWith(DETAIL_MESSAGE_KEY);
		}
	}

	private class IdentifiedMessageCriteria implements MessageCriteria {

		String nullSummaryId = null + SUMMARY_MESSAGE_KEY;

		private Set<String> identifiedMessageSources = new HashSet<String>();

		// From getClientIdsWithMessages docs: If any messages have been queued that were not associated with
		// any specific client identifier, a null value will be included in the iterated values.
		public boolean test(Message message) {
			if (message.getSource() != null && message.getSource().toString().endsWith(DETAIL_MESSAGE_KEY)) {
				return false;
			} else if (message.getSource() == null || message.getSource().equals("")
					|| message.getSource().equals(nullSummaryId)) {
				return identifiedMessageSources.add(null);
			}
			return identifiedMessageSources.add((String) message.getSource());
		}
	}

	/**
	 * Adapter class to convert a {@link FacesMessage} to a Spring {@link Message}. This adapter is required to allow
	 * <tt>FacesMessages</tt> to be registered with spring while still retaining their mutable nature. It is not
	 * uncommon for <tt>FacesMessages</tt> to be changed after they have been added to a <tt>FacesContext</tt>, for
	 * example, from a <tt>PhaseListener</tt>.
	 * <p>
	 * NOTE: Only {@link javax.faces.application.FacesMessage} instances are directly adapted, any subclasses will be
	 * converted to the standard FacesMessage implementation. This is to protect against bugs such as SWF-1073.
	 * 
	 * For convenience this class also implements the {@link MessageResolver} interface.
	 */
	private static class FlowFacesMessageAdapter extends Message implements MessageResolver {

		private String key;
		private String source;
		private transient FacesMessage facesMessage;

		public FlowFacesMessageAdapter(String source, String key, FacesMessage message) {
			super(null, null, null);
			this.source = source;
			this.key = key;
			this.facesMessage = asStandardFacesMessageInstance(message);
		}

		// Custom serialization to work around myfaces bug MYFACES-1347

		private void writeObject(ObjectOutputStream oos) throws IOException {
			oos.defaultWriteObject();
			oos.writeObject(facesMessage.getSummary());
			oos.writeObject(facesMessage.getDetail());
			oos.writeInt(facesMessage.getSeverity().getOrdinal());
		}

		private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
			ois.defaultReadObject();
			String summary = (String) ois.readObject();
			String detail = (String) ois.readObject();
			int severityOrdinal = ois.readInt();
			FacesMessage.Severity severity = FacesMessage.SEVERITY_INFO;
			for (Iterator<?> iterator = FacesMessage.VALUES.iterator(); iterator.hasNext();) {
				FacesMessage.Severity value = (FacesMessage.Severity) iterator.next();
				if (value.getOrdinal() == severityOrdinal) {
					severity = value;
				}
			}
			facesMessage = new FacesMessage(severity, summary, detail);
		}

		/**
		 * Use standard faces message as required to protect against bugs such as SWF-1073.
		 * 
		 * @param message {@link javax.faces.application.FacesMessage} or subclass.
		 * @return {@link javax.faces.application.FacesMessage} instance
		 */
		private FacesMessage asStandardFacesMessageInstance(FacesMessage message) {
			if (FacesMessage.class.equals(message.getClass())) {
				return message;
			}
			return new FacesMessage(message.getSeverity(), message.getSummary(), message.getDetail());
		}

		public Object getSource() {
			return source + key;
		}

		public String getText() {
			String text = null;
			if (DETAIL_MESSAGE_KEY.equals(key)) {
				text = facesMessage.getDetail();
			} else if (SUMMARY_MESSAGE_KEY.equals(key)) {
				text = facesMessage.getSummary();
			} else {
				throw new RuntimeException("Unknown faces message type key");
			}

			if (StringUtils.hasText(text)) {
				return text;
			}
			return "";
		}

		public Severity getSeverity() {
			Severity severity = null;
			if (facesMessage.getSeverity() != null) {
				severity = FACESSEVERITY_TO_SPRINGSEVERITY.get(facesMessage.getSeverity());
			}
			return (severity == null ? Severity.INFO : severity);
		}

		public String toString() {
			ToStringCreator rtn = new ToStringCreator(this);
			rtn.append("severity", getSeverity());
			if (FacesContext.getCurrentInstance() != null) {
				// Only append text if running within a faces context
				rtn.append("text", getText());
			}
			return rtn.toString();
		}

		public Message resolveMessage(MessageSource messageSource, Locale locale) {
			return this;
		}

		/**
		 * @return The original {@link FacesMessage} adapted by this class.
		 */
		public FacesMessage getFacesMessage() {
			return facesMessage;
		}

	}
}
