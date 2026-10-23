package com.netswitch.widget;

interface INetService {
    // Код транзакции, который Shizuku использует для остановки сервиса
    void destroy() = 16777114;

    // Текущая маска разрешённых типов сети, -1 при ошибке
    long getAllowedTypes() = 1;

    // null при успехе, иначе текст ошибки
    String setAllowedTypes(long mask) = 2;
}
