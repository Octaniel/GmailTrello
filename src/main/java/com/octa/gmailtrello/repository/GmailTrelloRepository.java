package com.octa.gmailtrello.repository;

import com.octa.gmailtrello.model.GmailTrello;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailTrelloRepository extends JpaRepository<GmailTrello, Long> {
}
