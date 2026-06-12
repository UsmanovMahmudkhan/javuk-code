package dev.javuk.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.javuk.agent.Conversation;

import java.util.List;

/** A persisted conversation: an id, the model used, and the full transcript. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Session(String id, String model, String createdAt, List<Conversation.Entry> entries) {

    public static Session of(String id, String model, String createdAt, Conversation conversation) {
        return new Session(id, model, createdAt, conversation.entries());
    }
}
