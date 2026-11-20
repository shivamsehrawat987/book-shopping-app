package com.bookstore.book_shopping_app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.admin.bootstrap.enabled=false")
class BookShoppingAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
