package smoketests;

import java.util.random.RandomGenerator;

public class UserNameRandomGenerator {
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();

    public String generate() {
        var sb = new StringBuilder(10);
        // First character: always a letter
        sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        for (int i = 0; i < 9; i++) {
            if (RANDOM.nextBoolean()) {
                sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
            } else {
                sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
            }
        }
        return sb.toString();
    }
}
