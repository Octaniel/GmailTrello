package com.octa.gmailtrello.resource;

import com.octa.gmailtrello.model.GmailTrello;
import com.octa.gmailtrello.repository.GmailTrelloRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("gmailTrello")
public class GmailTrelloResource {
    private final GmailTrelloRepository gmailTrelloRepository;

    public GmailTrelloResource(GmailTrelloRepository gmailTrelloRepository) {
        this.gmailTrelloRepository = gmailTrelloRepository;
    }

    @GetMapping
    public Page<GmailTrello> findAll(Pageable pageable) {
        return gmailTrelloRepository.findAll(pageable);
    }

}
