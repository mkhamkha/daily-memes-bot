package ru.mkhamkha.dmb.service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.mkhamkha.dmb.config.BotProperties;
import ru.mkhamkha.dmb.other.DmbException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static ru.mkhamkha.dmb.other.MessageText.*;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final BotProperties properties;
    private final List<Integer> remainingMemes;
    private final List<Integer> remainingMessages;
    private int lastMessageIndex;
    private int lastMemeIndex;
    private Long chatIdForDailyMem;

    public TelegramBot(BotProperties properties) {
        this.properties = properties;
        this.remainingMemes = new ArrayList<>();
        this.remainingMessages = new ArrayList<>();
        this.lastMessageIndex = -1;
        this.lastMemeIndex = -1;
    }

    @PostConstruct
    public void init() {
        List<BotCommand> listOfCommands = List.of(
                new BotCommand("/start", "Персональное предсказание"),
                new BotCommand("/daily_mem", "Запуск ежедневного предсказания"),
                new BotCommand("/stop_daily_mem", "Остановить ежедневные предсказания")
        );

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            throw new DmbException("Произошла ошибка в ходе выбора команд меню.");
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleIncomingMessage(update);
        }
    }

    @Override
    public String getBotUsername() {
        return properties.getName();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    private void handleIncomingMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        switch (messageText) {
            case "/start":
                startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                break;
            case "/daily_mem":
                startDailyMem(chatId);
                break;
            case "/stop_daily_mem":
                stopDailyMem(chatId);
                break;
            default:
                sendMessage(chatId, "Неизвестная команда. Используйте /start, /daily_mem или /stop_daily_mem.");
        }
    }

    private void startCommandReceived(long chatId, String name) {
        String answer = "Привет, " + name + ", вот твое персональное предсказание на день!";
        sendImage(chatId, selectMem());
        sendMessage(chatId, answer);
    }

    private void startDailyMem(long chatId) {
        if (chatIdForDailyMem != null) {
            sendMessage(chatId, "Ежедневный мемопоток уже запущен.");
        } else {
            chatIdForDailyMem = chatId;
            sendMessage(chatId, "Ежедневный мемопоток запущен. Мемы будут приходить каждое утро в 9:00.");
        }
    }

    private void stopDailyMem(long chatId) {
        if (chatIdForDailyMem != null) {
            chatIdForDailyMem = null;
            sendMessage(chatId, "Ежедневный мемопоток остановлен.");
        } else {
            sendMessage(chatId, "Ежедневный мемопоток не запущен.");
        }
    }

//    @Scheduled(cron = "0 0 9 * * ?")
    @Scheduled(cron = "*/5 * * * * ?")
    public void sendDailyMem() {
        if (chatIdForDailyMem != null) {
            String head = "Привет. Я приготовил для вас ежедневный мем!";
            sendMessage(chatIdForDailyMem, head);
            sendImage(chatIdForDailyMem, selectMem());
            sendMessage(chatIdForDailyMem, selectMessage());
        }
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new DmbException("Произошла ошибка в работе метода sendMessage");
        }
    }

    private void sendImage(long chatId, String fileName) {
        SendPhoto sendPhotoRequest = new SendPhoto();
        sendPhotoRequest.setChatId(String.valueOf(chatId));

        String path = "images/" + fileName;
        InputFile photo = new InputFile(getClass().getClassLoader().getResourceAsStream(path), fileName);
        sendPhotoRequest.setPhoto(photo);

        try {
            execute(sendPhotoRequest);
        } catch (TelegramApiException e) {
            throw new DmbException("Произошла ошибка в работе метода sendImage.");
        }
    }

    private String selectMessage() {
        List<String> allMessages = List.of(
                MESSAGE_0, MESSAGE_1, MESSAGE_2, MESSAGE_3, MESSAGE_4,
                MESSAGE_5, MESSAGE_6, MESSAGE_7, MESSAGE_8, MESSAGE_9
        );

        if (remainingMessages.isEmpty()) {
            IntStream.range(0, allMessages.size())
                    .filter(i -> i != lastMessageIndex)
                    .forEach(remainingMessages::add);
        }

        if (remainingMessages.isEmpty()) {
            IntStream.range(0, allMessages.size())
                    .forEach(remainingMessages::add);
            if (lastMessageIndex >= 0 && lastMessageIndex < allMessages.size()) {
                remainingMessages.remove(Integer.valueOf(lastMessageIndex));
            }
        }


        Random random = new Random();
        int index = random.nextInt(remainingMessages.size());
        int messageIndex = remainingMessages.remove(index);
        lastMessageIndex = messageIndex;

        return allMessages.get(messageIndex);
    }

    /**
     * Выбирает случайный мем из доступного набора, обеспечивая отсутствие повторов в текущем сеансе.
     * Если все мемы уже были использованы, список перезаполняется, исключая последний выбранный мем.
     *
     * @return имя файла выбранного мема в формате "memas-{index}.jpg"
     */
    private String selectMem() {
        if (remainingMemes.isEmpty()) {
            IntStream.rangeClosed(0, 10)
                    .filter(i -> i != lastMemeIndex)
                    .forEach(remainingMemes::add);
        }

        if (remainingMemes.isEmpty()) {
            IntStream.rangeClosed(0, 10)
                    .forEach(remainingMemes::add);
            if (lastMemeIndex >= 0 && lastMemeIndex <= 10) {
                remainingMemes.remove(Integer.valueOf(lastMemeIndex));
            }
        }

        Random random = new Random();
        int index = random.nextInt(remainingMemes.size());
        int memeIndex = remainingMemes.remove(index);
        lastMemeIndex = memeIndex;

        return "memas-" + memeIndex + ".jpg";
    }
}
