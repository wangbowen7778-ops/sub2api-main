package test;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class TestLombok {
    private String name;
    private int age;

    public static void main(String[] args) {
        TestLombok t = new TestLombok();
        t.setName("test");
        t.setAge(25);
        System.out.println(t.getName() + " - " + t.getAge());
        log.info("Hello from Lombok!");
    }
}