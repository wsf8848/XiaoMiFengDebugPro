#include "stm32f10x.h"                  // Device header
#include "KEY.h"  
#include "Delay.h" 

void Key_Init(void)
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA,ENABLE);
	
	GPIO_InitTypeDef GPIO_InitStusture;
	GPIO_InitStusture.GPIO_Mode = GPIO_Mode_IPU;
	GPIO_InitStusture.GPIO_Pin = GPIO_Pin_10|GPIO_Pin_15;
	GPIO_InitStusture.GPIO_Speed = GPIO_Speed_50MHz;
	
	GPIO_Init(GPIOA,&GPIO_InitStusture);
	
}

uint8_t Key_Get_Num(void)
{
	uint8_t Key_Num = 0;
	
	//PA10
	if(GPIO_ReadInputDataBit(GPIOA,GPIO_Pin_10)==0)
	{
		Delay_ms(20);
		while(GPIO_ReadInputDataBit(GPIOA,GPIO_Pin_10)==0);
		Delay_ms(20);
		Key_Num = 1;
		
	}
	//PA15
	if(GPIO_ReadInputDataBit(GPIOA,GPIO_Pin_15)==0)
	{
		Delay_ms(20);
		while(GPIO_ReadInputDataBit(GPIOA,GPIO_Pin_15)==0);
		Delay_ms(20);
		Key_Num = 2;
		
	}
	
	return Key_Num;
	
	
}