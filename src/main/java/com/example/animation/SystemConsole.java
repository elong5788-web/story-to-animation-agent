package com.example.animation;

import java.util.Scanner;

/** 命令行实现:包装 System.out / System.in。 */
public class SystemConsole implements Console {
    private final Scanner sc = new Scanner(System.in);

    @Override
    public void print(String s) {
        System.out.print(s);
    }

    @Override
    public void println(String s) {
        System.out.println(s);
    }

    @Override
    public String readLine() {
        return sc.hasNextLine() ? sc.nextLine() : null;  // EOF 返回 null,让调用方安全退出
    }
}
