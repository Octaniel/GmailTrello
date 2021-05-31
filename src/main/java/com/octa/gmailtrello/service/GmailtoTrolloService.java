package com.octa.gmailtrello.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.octa.gmailtrello.model.GmailTrello;
import com.octa.gmailtrello.repository.GmailTrelloRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.Thread;

import io.restassured.path.json.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GmailtoTrolloService {
    Logger logger = LoggerFactory.getLogger(GmailtoTrolloService.class);

    @Value("trello.token")
    String tokenTrello;

    @Value("trello.key")
    String keyTrello;

    @Value("trello.idList")
    String adListTrello;

    private static final String APPLICATION_NAME = "gmailtrello";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String USER_ID = "me";

    private final GmailTrelloRepository gmailTrelloRepository;
    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.MAIL_GOOGLE_COM);
    private static final String CREDENTIALS_FILE_PATH =
            System.getProperty("user.dir") +
                    File.separator + "src" +
                    File.separator + "main" +
                    File.separator + "resources" +
                    File.separator + "credentials" +
                    File.separator + "credentials_new.json";

    private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.dir") +
            File.separator + "src" +
            File.separator + "main" +
            File.separator + "resources" +
            File.separator + "credentials";

    public GmailtoTrolloService(GmailTrelloRepository gmailTrelloRepository) {
        this.gmailTrelloRepository = gmailTrelloRepository;
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(new File(CREDENTIALS_FILE_PATH));
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(9999).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }


    public static Gmail getService() throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
        return service;
    }

    public static List<Message> listMessagesMatchingQuery(Gmail service, String userId,
                                                          String query) throws IOException {
        ListMessagesResponse response = service.users().messages().list(userId).setQ(query).execute();
        List<Message> messages = new ArrayList<Message>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = service.users().messages().list(userId).setQ(query)
                        .setPageToken(pageToken).execute();
            } else {
                break;
            }
        }
        return messages;
    }

    public static Message getMessage(Gmail service, String userId, List<Message> messages, int index)
            throws IOException {
        Message message = service.users().messages().get(userId, messages.get(index).getId()).execute();
        return message;
    }

    public static List<HashMap<String, String>> getGmailData(String query) {
        try {
            List<HashMap<String, String>> hashMaps = new ArrayList<>();
            Gmail service = getService();
            List<Message> messages = listMessagesMatchingQuery(service, USER_ID, query);
            for (int i = 0; i < messages.size(); i++) {
                Message message = getMessage(service, USER_ID, messages, i);
                JsonPath jp = new JsonPath(message.toString());
                String subject = jp.getString("payload.headers.find { it.name == 'Subject' }.value");
                String body = new String(Base64.getDecoder().decode(jp.getString("payload.parts[0].body.data")));
                String link = null;
                String arr[] = body.split("\n");
                for (String s : arr) {
                    s = s.trim();
                    if (s.startsWith("http") || s.startsWith("https")) {
                        link = s.trim();
                    }
                }
                HashMap<String, String> hm = new HashMap<String, String>();
                hm.put("subject", subject);
                hm.put("body", body);
                hm.put("link", link);
                hashMaps.add(hm);
            }
            return hashMaps;
        } catch (Exception e) {
            System.out.println("email not found....");
            throw new RuntimeException(e);
        }
    }

    public static int getTotalCountOfMails() {
        int size;
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            List<Thread> threads = service.
                    users().
                    threads().
                    list("me").
                    execute().
                    getThreads();
            size = threads.size();
        } catch (Exception e) {
            System.out.println("Exception log " + e);
            size = -1;
        }
        return size;
    }

    public static boolean isMailExist(String messageTitle) {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            ListMessagesResponse response = service.
                    users().
                    messages().
                    list("me").
                    setQ("subject:" + messageTitle).
                    execute();
            List<Message> messages = getMessages(response);
            return messages.size() != 0;
        } catch (Exception e) {
            System.out.println("Exception log" + e);
            return false;
        }
    }

    private static List<Message> getMessages(ListMessagesResponse response) {
        List<Message> messages = new ArrayList<Message>();
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            while (response.getMessages() != null) {
                messages.addAll(response.getMessages());
                if (response.getNextPageToken() != null) {
                    String pageToken = response.getNextPageToken();
                    response = service.users().messages().list(USER_ID)
                            .setPageToken(pageToken).execute();
                } else {
                    break;
                }
            }
            return messages;
        } catch (Exception e) {
            System.out.println("Exception log " + e);
            return messages;
        }
    }

    @Scheduled(cron = "0 0/1 * * * MON-FRI")
    public void verificar() {
        System.out.println(LocalDateTime.now().getMinute());
        logger.info("Verificando o email");
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toEpochSecond();
        List<HashMap<String, String>> trello = getGmailData("\"Trello\" after:" + epoch);
        if (trello.size() == 0) {
            logger.info("Nenhum email para enviar para o trello");
        }
        trello.forEach(hm -> {
            HttpResponse<String> response;
            try {
                response = Unirest.post("https://api.trello.com/1/cards")
                        .queryString("key", keyTrello)
                        .queryString("token", tokenTrello)
                        .queryString("idList", adListTrello)
                        .queryString("name", hm.get("subject"))
                        .queryString("desc", hm.get("body"))
                        .asString();
                System.out.println(response.getBody());
                GmailTrello gmailTrello = new GmailTrello();
                gmailTrello.setDescription(hm.get("body"));
                gmailTrello.setTitle(hm.get("subject"));
                gmailTrelloRepository.save(gmailTrello);
            } catch (UnirestException e) {
                System.out.println("Erro ao publicar no trello");
            }
        });
    }

}
