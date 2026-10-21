package io.consulting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {

  public static void main(String[] args) {
    new java.io.File("data").mkdirs();
    SpringApplication.run(App.class, args);
  }
}
