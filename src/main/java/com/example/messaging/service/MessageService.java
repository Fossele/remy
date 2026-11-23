package com.example.messaging.service;


import com.example.messaging.dto.ChatMessageResponse;
import com.example.messaging.entity.ChatMessage;
import com.example.messaging.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageRepository chatMessageRepository;

     public Page<ChatMessageResponse>  getMessages(UUID channelId, Instant startTime, Instant endTime, Pageable pageable){
        Page<ChatMessage> messages = chatMessageRepository.findByChannelIdAndTimestampBetween(channelId, startTime, endTime, pageable);
         return  messages.map(ChatMessageResponse::fromEntity);
    }
}
