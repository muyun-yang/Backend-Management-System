// package com.example.model;

// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;
// import java.io.Serializable;
// import java.util.UUID;

// @Data
// @NoArgsConstructor
// @AllArgsConstructor
// public class Message implements Serializable {
//     private static final long serialVersionUID = 1L;

//     private String id = UUID.randomUUID().toString();
//     private String content;
//     private MessageType type;

//     // 为方便调用而添加的构造函数
//     public Message(MessageType type, String content) {
//         this.id = UUID.randomUUID().toString();
//         this.type = type;
//         this.content = content;
//     }
// }

package com.example.model;

import java.io.Serializable;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String content;
    private MessageType type;

    // 无参构造函数（Jackson 反序列化需要）
    public Message() {
        this.id = UUID.randomUUID().toString();
    }

    // 全参构造函数
    public Message(String id, String content, MessageType type) {
        this.id = id;
        this.content = content;
        this.type = type;
    }

    // 方便调用的构造函数
    public Message(MessageType type, String content) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.content = content;
    }

    // 标准 Getter 和 Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    @Override
    public String toString() {
        return "Message{" + "id='" + id + '\'' + ", content='" + content + '\'' + ", type=" + type + '}';
    }
}