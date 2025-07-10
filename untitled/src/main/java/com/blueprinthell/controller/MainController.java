package com.blueprinthell.controller;

import javax.swing.*;

public class MainController {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // پنجرهٔ اصلی
            JFrame frame = new JFrame("BlueprintHell");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            // ثابت کردن اندازه و جلوگیری از تغییر سایز
            frame.setResizable(false);
            frame.setSize(800, 650);

            // 1. راه‌اندازی ScreenController
            ScreenController screenController = new ScreenController(frame);

            // 2. ساخت GameController (بدون شروع بازی)
            GameController gameController     = new GameController(screenController);

            // 3. ساخت MenuController تا دکمه‌ها به لیسنرها وصل شوند
            new MenuController(screenController, gameController);

            // 4. نمایش منوی اصلی
            screenController.showScreen(ScreenController.MAIN_MENU);

            // 5. نمایش پنجره
            frame.setVisible(true);
        });
    }
}
