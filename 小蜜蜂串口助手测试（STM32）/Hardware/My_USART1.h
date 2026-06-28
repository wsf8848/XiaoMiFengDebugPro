#ifndef __MY_USART3_H
#define __MY_USART3_H

#include <stdio.h>
#include "stm32f10x.h"                  // Device header

void My_USART3_Init(void);
void My_USART3_SendByte(uint8_t Byte);
void My_USART3_SendArray(uint8_t *Array, uint16_t Length);
void My_USART3_SendString(char *String);
void My_USART3_SendNumber(uint32_t Number, uint8_t Length);
void My_USART3_Printf(char *format, ...);

#endif
