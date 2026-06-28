#include "stm32f10x.h"                  // Device header
#include "LED.h"  

void LED_Init(void)
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA|RCC_APB2Periph_GPIOC,ENABLE);
	
	//PA9
	GPIO_InitTypeDef GPIO_InitStrusture;
	GPIO_InitStrusture.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStrusture.GPIO_Pin = GPIO_Pin_9;
	GPIO_InitStrusture.GPIO_Speed = GPIO_Speed_50MHz;
	
	GPIO_Init(GPIOA,&GPIO_InitStrusture);
	
	//PC13
	GPIO_InitStrusture.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStrusture.GPIO_Pin = GPIO_Pin_13;
	GPIO_InitStrusture.GPIO_Speed = GPIO_Speed_50MHz;
	
	GPIO_Init(GPIOC,&GPIO_InitStrusture);
	
	//默认高电平状态
	GPIO_SetBits(GPIOA,GPIO_Pin_9);
	GPIO_SetBits(GPIOC,GPIO_Pin_13);
}

void LED1_ON(void)
{
	GPIO_ResetBits(GPIOA,GPIO_Pin_9);
}

void LED1_OFF(void)
{
	GPIO_SetBits(GPIOA,GPIO_Pin_9);
}

void LED1_Turn(void)
{
	if(GPIO_ReadOutputDataBit(GPIOA,GPIO_Pin_9)==0)
	{
		GPIO_SetBits(GPIOA,GPIO_Pin_9);
	}
	else
	{
		GPIO_ResetBits(GPIOA,GPIO_Pin_9);
	}
}

void LED2_ON(void)
{
	GPIO_ResetBits(GPIOC,GPIO_Pin_13);
}

void LED2_OFF(void)
{
	GPIO_SetBits(GPIOC,GPIO_Pin_13);
}

void LED2_Turn(void)
{
	if(GPIO_ReadOutputDataBit(GPIOC,GPIO_Pin_13)==0)
	{
		GPIO_SetBits(GPIOC,GPIO_Pin_13);
	}
	else
	{
		GPIO_ResetBits(GPIOC,GPIO_Pin_13);
	}
}