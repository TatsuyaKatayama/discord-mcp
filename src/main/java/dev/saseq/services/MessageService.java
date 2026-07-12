package dev.saseq.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

@Service
public class MessageService {

    private final JDA jda;

    @Value("${DISCORD_MCP_STATE_FILE:/workspace/.discord_mcp_state.properties}")
    private String checkpointStateFile;

    @Value("${DISCORD_LAST_MESSAGE_ID:}")
    private String initialLastMessageId;

    public MessageService(JDA jda) {
        this.jda = jda;
    }

    /**
     * Helper method to get a MessageChannel by ID, checking both text channels and thread channels.
     */
    private MessageChannel getMessageChannelById(String channelId) {
        // First try text channel
        TextChannel textChannel = jda.getTextChannelById(channelId);
        if (textChannel != null) {
            return textChannel;
        }
        // Then try news/announcement channel
        NewsChannel newsChannel = jda.getNewsChannelById(channelId);
        if (newsChannel != null) {
            return newsChannel;
        }
        // Then try thread channel
        ThreadChannel threadChannel = jda.getThreadChannelById(channelId);
        if (threadChannel != null) {
            return threadChannel;
        }
        return null;
    }

    /**
     * Sends a message to a specified Discord channel.
     *
     * @param channelId The ID of the channel where the message will be sent.
     * @param message   The content of the message to be sent.
     * @return A confirmation message with a link to the sent message.
     */
    @Tool(name = "send_message", description = "Send a message to a specific channel")
    public String sendMessage(@ToolParam(description = "Discord channel ID") String channelId,
                              @ToolParam(description = "Message content") String message) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message sentMessage = channel.sendMessage(message).complete();
        return "Message sent successfully. Message link: " + sentMessage.getJumpUrl();
    }

    /**
     * Sends a local file to a specified Discord channel.
     *
     * @param channelId The ID of the channel where the file will be sent.
     * @param filePath  The local file path inside the discord-mcp container.
     * @param message   Optional message content to send with the file.
     * @return A confirmation message with a link to the sent message.
     */
    @Tool(name = "send_file", description = "Send a local file to a specific channel. The file path must be readable inside the discord-mcp container.")
    public String sendFile(@ToolParam(description = "Discord channel ID") String channelId,
                           @ToolParam(description = "Local file path inside the discord-mcp container") String filePath,
                           @ToolParam(description = "Optional message content", required = false) String message) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }

        Path path = validateReadableFile(filePath);
        Message sentMessage = channel.sendMessage(safeMessage(message))
                .addFiles(FileUpload.fromData(path))
                .complete();
        return "File sent successfully. Message link: " + sentMessage.getJumpUrl();
    }

    /**
     * Replies to a specific Discord message with a local file.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message to reply to.
     * @param filePath  The local file path inside the discord-mcp container.
     * @param message   Optional message content to send with the file.
     * @return A confirmation message with a link to the sent reply.
     */
    @Tool(name = "reply_file", description = "Reply to a specific message with a local file. The file path must be readable inside the discord-mcp container.")
    public String replyFile(@ToolParam(description = "Discord channel ID") String channelId,
                            @ToolParam(description = "Discord message ID to reply to") String messageId,
                            @ToolParam(description = "Local file path inside the discord-mcp container") String filePath,
                            @ToolParam(description = "Optional message content", required = false) String message) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message messageById = channel.retrieveMessageById(messageId).complete();
        if (messageById == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }

        Path path = validateReadableFile(filePath);
        Message sentMessage = messageById.reply(safeMessage(message))
                .addFiles(FileUpload.fromData(path))
                .complete();
        return "File reply sent successfully. Message link: " + sentMessage.getJumpUrl();
    }

    /**
     * Edits an existing message in a specified Discord channel.
     *
     * @param channelId  The ID of the channel containing the message.
     * @param messageId  The ID of the message to be edited.
     * @param newMessage The new content for the message.
     * @return A confirmation message with a link to the edited message.
     */
    @Tool(name = "edit_message", description = "Edit a message from a specific channel")
    public String editMessage(@ToolParam(description = "Discord channel ID") String channelId,
                              @ToolParam(description = "Specific message ID") String messageId,
                              @ToolParam(description = "New message content") String newMessage) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }
        if (newMessage == null || newMessage.isEmpty()) {
            throw new IllegalArgumentException("newMessage cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message messageById = channel.retrieveMessageById(messageId).complete();
        if (messageById == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        Message editedMessage = messageById.editMessage(newMessage).complete();
        return "Message edited successfully. Message link: " + editedMessage.getJumpUrl();
    }

    /**
     * Deletes a message from a specified Discord channel.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message to be deleted.
     * @return A confirmation message indicating the message was deleted successfully.
     */
    @Tool(name = "delete_message", description = "Delete a message from a specific channel")
    public String deleteMessage(@ToolParam(description = "Discord channel ID") String channelId,
                                @ToolParam(description = "Specific message ID") String messageId) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message messageById = channel.retrieveMessageById(messageId).complete();
        if (messageById == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        messageById.delete().queue();
        return "Message deleted successfully";
    }

    /**
     * Reads message history from a specified Discord channel.
     *
     * @param channelId The ID of the channel from which to read messages.
     * @param count     Optional number of messages to retrieve (default is 100, max is 100).
     * @param before    Optional message ID to fetch messages before this message.
     * @param after     Optional message ID to fetch messages after this message.
     * @param around    Optional message ID to fetch messages around this message.
     * @return A formatted string containing the retrieved messages.
     */
    @Tool(name = "read_messages", description = "Read message history from a specific channel, optionally paginated with before/after/around")
    public String readMessages(@ToolParam(description = "Discord channel ID") String channelId,
                               @ToolParam(description = "Number of messages to retrieve (1-100)", required = false) String count,
                               @ToolParam(description = "Message ID to fetch messages before this message", required = false) String before,
                               @ToolParam(description = "Message ID to fetch messages after this message", required = false) String after,
                               @ToolParam(description = "Message ID to fetch messages around this message", required = false) String around) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        int limit = parseMessageLimit(count);
        validateCursorParameters(before, after, around);

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        List<Message> messages;
        if (isProvided(before)) {
            messages = channel.getHistoryBefore(before, limit).complete().getRetrievedHistory();
        } else if (isProvided(after)) {
            messages = channel.getHistoryAfter(after, limit).complete().getRetrievedHistory();
        } else if (isProvided(around)) {
            messages = channel.getHistoryAround(around, limit).complete().getRetrievedHistory();
        } else {
            messages = channel.getHistory().retrievePast(limit).complete();
        }
        List<String> formatedMessages = formatMessages(messages);
        return "**Retrieved " + messages.size() + " messages:** \n" + String.join("\n", formatedMessages);
    }

    /**
     * Reads only messages newer than the persisted checkpoint for a Discord channel.
     * The checkpoint is stored in DISCORD_MCP_STATE_FILE and updated to the latest seen
     * message ID after each call. DISCORD_LAST_MESSAGE_ID is used as the initial
     * checkpoint when no state file entry exists.
     *
     * @param channelId          The ID of the channel from which to read messages.
     * @param count              Optional number of messages to retrieve (default is 100, max is 100).
     * @param checkpointKey      Optional state key. Defaults to the channel ID.
     * @param includeBotMessages Optional boolean string. Defaults to false, excluding this bot's own messages from the returned output.
     * @return A formatted string containing only new non-bot messages and checkpoint details.
     */
    @Tool(name = "read_new_messages", description = "Read only messages newer than the persisted checkpoint and update the checkpoint state file.")
    public String readNewMessages(@ToolParam(description = "Discord channel ID") String channelId,
                                  @ToolParam(description = "Number of messages to retrieve (1-100)", required = false) String count,
                                  @ToolParam(description = "Optional checkpoint key. Defaults to channelId", required = false) String checkpointKey,
                                  @ToolParam(description = "Whether to include this bot's own messages. Defaults to false", required = false) String includeBotMessages) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        int limit = parseMessageLimit(count);

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }

        String stateKey = isProvided(checkpointKey) ? checkpointKey : channelId;
        String previousCheckpoint = loadMessageCheckpoint(stateKey);
        List<Message> seenMessages;
        if (isProvided(previousCheckpoint)) {
            seenMessages = channel.getHistoryAfter(previousCheckpoint, limit).complete().getRetrievedHistory();
        } else {
            seenMessages = channel.getHistory().retrievePast(limit).complete();
        }

        List<Message> inboundMessages = seenMessages.stream()
                .filter(message -> !isOwnBotMessage(message))
                .toList();
        String nextCheckpoint = newestMessageId(previousCheckpoint, inboundMessages);
        if (isProvided(nextCheckpoint) && !nextCheckpoint.equals(previousCheckpoint)) {
            saveMessageCheckpoint(stateKey, nextCheckpoint);
        }

        boolean includeOwnBotMessages = Boolean.parseBoolean(includeBotMessages);
        List<Message> returnedMessages = seenMessages.stream()
                .filter(message -> includeOwnBotMessages || !isOwnBotMessage(message))
                .sorted(Comparator.comparing(Message::getId, MessageService::compareSnowflake))
                .toList();

        List<String> formattedMessages = formatMessages(returnedMessages);
        return "**Retrieved " + returnedMessages.size() + " new messages:**"
                + "\nPrevious checkpoint: " + printableCheckpoint(previousCheckpoint)
                + "\nCurrent checkpoint: " + printableCheckpoint(nextCheckpoint)
                + "\nCheckpoint source: newest inbound non-bot message"
                + "\nState file: " + checkpointStateFile
                + "\n" + String.join("\n", formattedMessages);
    }

    /**
     * Manually sets the persisted message checkpoint for a channel or checkpoint key.
     *
     * @param channelId     The Discord channel ID. Used as the default checkpoint key.
     * @param messageId     The message ID to persist as the last checked checkpoint.
     * @param checkpointKey Optional state key. Defaults to channelId.
     * @return A confirmation message.
     */
    @Tool(name = "set_message_checkpoint", description = "Persist the last checked Discord message ID for a channel or checkpoint key.")
    public String setMessageCheckpoint(@ToolParam(description = "Discord channel ID") String channelId,
                                       @ToolParam(description = "Discord message ID to store as last checked") String messageId,
                                       @ToolParam(description = "Optional checkpoint key. Defaults to channelId", required = false) String checkpointKey) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        String stateKey = isProvided(checkpointKey) ? checkpointKey : channelId;
        saveMessageCheckpoint(stateKey, messageId);
        return "Message checkpoint saved. Key: " + stateKey + ", message ID: " + messageId + ", state file: " + checkpointStateFile;
    }

    private int parseMessageLimit(String count) {
        if (count == null || count.isBlank()) {
            return 100;
        }

        int limit;
        try {
            limit = Integer.parseInt(count);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("count must be an integer between 1 and 100");
        }

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100");
        }
        return limit;
    }

    private void validateCursorParameters(String before, String after, String around) {
        if (before != null && before.isBlank()) {
            throw new IllegalArgumentException("before cannot be blank");
        }
        if (after != null && after.isBlank()) {
            throw new IllegalArgumentException("after cannot be blank");
        }
        if (around != null && around.isBlank()) {
            throw new IllegalArgumentException("around cannot be blank");
        }

        int providedCursors = (isProvided(before) ? 1 : 0)
                + (isProvided(after) ? 1 : 0)
                + (isProvided(around) ? 1 : 0);
        if (providedCursors > 1) {
            throw new IllegalArgumentException("before, after, and around are mutually exclusive; provide only one");
        }
    }

    private boolean isProvided(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isOwnBotMessage(Message message) {
        return message.getAuthor() != null
                && jda.getSelfUser() != null
                && message.getAuthor().getId().equals(jda.getSelfUser().getId());
    }

    private String newestMessageId(String currentCheckpoint, List<Message> messages) {
        String newest = currentCheckpoint;
        for (Message message : messages) {
            if (!isProvided(newest) || compareSnowflake(message.getId(), newest) > 0) {
                newest = message.getId();
            }
        }
        return newest;
    }

    private synchronized String loadMessageCheckpoint(String key) {
        Properties properties = loadCheckpointProperties();
        String saved = properties.getProperty(checkpointPropertyName(key));
        if (isProvided(saved)) {
            return saved;
        }
        return isProvided(initialLastMessageId) ? initialLastMessageId : "";
    }

    private synchronized void saveMessageCheckpoint(String key, String messageId) {
        Properties properties = loadCheckpointProperties();
        properties.setProperty(checkpointPropertyName(key), messageId);
        Path path = Path.of(checkpointStateFile);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "discord-mcp message checkpoints");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save message checkpoint state file: " + checkpointStateFile, ex);
        }
    }

    private Properties loadCheckpointProperties() {
        Properties properties = new Properties();
        Path path = Path.of(checkpointStateFile);
        if (!Files.isRegularFile(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load message checkpoint state file: " + checkpointStateFile, ex);
        }
    }

    private String checkpointPropertyName(String key) {
        return "message." + key + ".lastMessageId";
    }

    private String printableCheckpoint(String checkpoint) {
        return isProvided(checkpoint) ? checkpoint : "(none)";
    }

    private static int compareSnowflake(String left, String right) {
        if (left.equals(right)) {
            return 0;
        }
        try {
            return Long.compareUnsigned(Long.parseUnsignedLong(left), Long.parseUnsignedLong(right));
        } catch (NumberFormatException ex) {
            return left.compareTo(right);
        }
    }

    private Path validateReadableFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath cannot be null");
        }
        Path path = Path.of(filePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File not found by filePath");
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable");
        }
        return path;
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? " " : message;
    }

    /**
     * Adds a reaction (emoji) to a specific message in a Discord channel.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message to which the reaction will be added.
     * @param emoji     The emoji to add as a reaction (can be a Unicode character or a custom emoji string).
     * @return A confirmation message with a link to the message that was reacted to.
     */
    @Tool(name = "add_reaction", description = "Add a reaction (emoji) to a specific message")
    public String addReaction(@ToolParam(description = "Discord channel ID") String channelId,
                              @ToolParam(description = "Discord message ID") String messageId,
                              @ToolParam(description = "Emoji (Unicode or string)") String emoji) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }
        if (emoji == null || emoji.isEmpty()) {
            throw new IllegalArgumentException("emoji cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message message = channel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        message.addReaction(Emoji.fromUnicode(emoji)).queue();
        return "Added reaction successfully. Message link: " + message.getJumpUrl();
    }

    /**
     * Removes a specified reaction (emoji) from a message in a Discord channel.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message from which the reaction will be removed.
     * @param emoji     The emoji to remove from the message (can be a Unicode character or a custom emoji string).
     * @return A confirmation message with a link to the message.
     */
    @Tool(name = "remove_reaction", description = "Remove a specified reaction (emoji) from a message")
    public String removeReaction(@ToolParam(description = "Discord channel ID") String channelId,
                                 @ToolParam(description = "Discord message ID") String messageId,
                                 @ToolParam(description = "Emoji (Unicode or string)") String emoji) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }
        if (emoji == null || emoji.isEmpty()) {
            throw new IllegalArgumentException("emoji cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message message = channel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        message.removeReaction(Emoji.fromUnicode(emoji)).queue();
        return "Removed reaction successfully. Message link: " + message.getJumpUrl();
    }

    /**
     * Retrieves attachment metadata from a specific message in a Discord channel.
     *
     * @param channelId    The ID of the channel containing the message.
     * @param messageId    The ID of the message to retrieve attachments from.
     * @param attachmentId Optional ID of a specific attachment (if omitted, returns all).
     * @return A formatted string containing attachment metadata.
     */
    @Tool(name = "get_attachment", description = "Get attachment metadata (filename, size, content type, URLs) from a specific message. Returns info only, does not download files.")
    public String getAttachment(@ToolParam(description = "Discord channel ID") String channelId,
                                @ToolParam(description = "Discord message ID") String messageId,
                                @ToolParam(description = "Specific attachment ID (omit to get all attachments)", required = false) String attachmentId) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        MessageChannel channel = getMessageChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        Message message = channel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }

        List<Message.Attachment> attachments = message.getAttachments();
        if (attachments.isEmpty()) {
            return "This message has no attachments.";
        }

        if (attachmentId != null && !attachmentId.isEmpty()) {
            Message.Attachment attachment = attachments.stream()
                    .filter(a -> a.getId().equals(attachmentId))
                    .findFirst()
                    .orElse(null);
            if (attachment == null) {
                throw new IllegalArgumentException("Attachment not found by attachmentId");
            }
            return formatAttachmentDetail(attachment);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Found ").append(attachments.size()).append(" attachment(s):**\n");
        for (Message.Attachment attachment : attachments) {
            sb.append(formatAttachmentDetail(attachment)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatAttachmentDetail(Message.Attachment attachment) {
        return String.format(
                "- %s\n  Proxy URL: %s",
                formatAttachmentSummary(attachment),
                attachment.getProxyUrl()
        );
    }

    private String formatAttachmentSummary(Message.Attachment attachment) {
        return String.format(
                "(Attachment ID: %s) `%s` (%s, %s) URL: %s",
                attachment.getId(),
                attachment.getFileName(),
                formatFileSize(attachment.getSize()),
                attachment.getContentType() != null ? attachment.getContentType() : "unknown",
                attachment.getUrl()
        );
    }

    private List<String> formatMessages(List<Message> messages) {
        return messages.stream()
                .map(m -> {
                    String authorName = m.getAuthor().getName();
                    String timestamp = m.getTimeCreated().toString();
                    String content = m.getContentDisplay();
                    String msgId = m.getId();

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("- (ID: %s) **[%s]** `%s`: ```%s```", msgId, authorName, timestamp, content));

                    List<Message.Attachment> attachments = m.getAttachments();
                    if (!attachments.isEmpty()) {
                        sb.append("\n  Attachments:");
                        for (Message.Attachment attachment : attachments) {
                            sb.append("\n    - ").append(formatAttachmentSummary(attachment));
                        }
                    }

                    return sb.toString();
                }).toList();
    }

    private String formatFileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
