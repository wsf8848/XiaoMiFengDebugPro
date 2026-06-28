#include "stm32f10x.h"                  // Device header
#include "Buzzer.h"   

void Buzzer_Init(void)
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA,ENABLE);
	
	//PA9
	GPIO_InitTypeDef GPIO_InitStrusture;
	GPIO_InitStrusture.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStrusture.GPIO_Pin = GPIO_Pin_5;
	GPIO_InitStrusture.GPIO_Speed = GPIO_Speed_50MHz;
	
	GPIO_Init(GPIOA,&GPIO_InitStrusture);
		
	//Ĭ�ϵ͵�ƽ״̬
	GPIO_ResetBits(GPIOA,GPIO_Pin_5);
}

void Buzzer_ON(void)
{
	GPIO_SetBits(GPIOA,GPIO_Pin_5);
}

void Buzzer_OFF(void)
{
	GPIO_ResetBits(GPIOA,GPIO_Pin_5);
}

void Buzzer_Turn(void)
{
	if(GPIO_ReadOutputDataBit(GPIOA,GPIO_Pin_5)==0)
	{
		GPIO_SetBits(GPIOA,GPIO_Pin_5);
	}
	else
	{
		GPIO_ResetBits(GPIOA,GPIO_Pin_5);
	}
}