package com.bookstore.book_shopping_app.dev;

import com.bookstore.book_shopping_app.dto.request.BookRequest;
import com.bookstore.book_shopping_app.mapper.BookMapper;
import com.bookstore.book_shopping_app.repository.BookRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class BookDataInitializer implements ApplicationRunner {
    private final BookRepository books;
    private final BookMapper mapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (var input = new ClassPathResource("data/books.json").getInputStream()) {
            BookRequest[] samples = objectMapper.readValue(input, BookRequest[].class);
            for (BookRequest sample : samples) {
                if (sample == null || !validator.validate(sample).isEmpty()) {
                    throw new IllegalStateException("Invalid book fixture in data/books.json");
                }
                String title = sample.title().trim();
                String author = sample.author().trim();
                if (books.findByTitleIgnoreCaseAndAuthorIgnoreCase(title, author).isEmpty()) {
                    books.save(mapper.toEntity(sample));
                }
            }
        }
    }
}