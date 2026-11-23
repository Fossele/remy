package com.example.messaging.controller;

import com.example.messaging.dto.ChatMessageResponse;
import com.example.messaging.entity.ChatMessage;
import com.example.messaging.repository.ChatMessageRepository;
import com.example.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;


import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/messages")
public class MessageHistoryController {

  private final MessageService messageService;

  @GetMapping("/{channelId}")
  public Page<ChatMessageResponse> getMessagesFromChannel(@PathVariable UUID channelId,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
                                                          @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

      Instant startTime = (start != null) ? start : Instant.EPOCH;
      Instant endTime = (end != null) ? end : Instant.now();

      // Map JPA Entities to Response DTO Records
      return messageService.getMessages(channelId, startTime,  endTime, pageable);

    }
}
