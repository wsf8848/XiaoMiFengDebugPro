#include "stm32f10x.h"     // Device header
#include "LED.h"  
#include "Delay.h" 
#include "KEY.h"  
#include "Buzzer.h"   
#include "OLED.h" 
#include "My_USART1.h"  

uint8_t Key_Num = 0;
extern uint8_t My_USART3_Data; 
static int8_t a = 0;

// 摇杆数据包模拟变量
uint8_t speed_left  = 10;    // 左轮速度
uint16_t gyro_angle = 90;    // 陀螺仪角度 0~180
uint8_t speed_right = 10;    // 右轮速度
uint8_t battery     = 80;    // 电量0~100
uint8_t mode        = 0;     // 运行模式
uint8_t state       = 0;     // 设备状态

// 自定义协议数据包模拟变量
uint8_t data1 = 1;
uint8_t data2 = 2;
uint8_t data3 = 3;

int main(void)
{
	/*模块初始化*/
	My_USART3_Init();
	LED_Init();
	Key_Init();
	Buzzer_Init();
	OLED_Init();
	
	OLED_ShowString(1, 1, "RX:");
	
	while (1)
	{
		//==================== 模拟数据自增循环 ====================
		// 左轮速度递增，超过100归零
		speed_left++;
		if(speed_left > 100)
		{
			speed_left = 0;
		}
		
		// 陀螺仪角度每次+5，超过180清零
		gyro_angle += 5;
		if(gyro_angle > 180)
		{
			gyro_angle = 0;
		}
		
		// 右轮速度每次+2，上限100
		speed_right += 2;
		if(speed_right > 100)
		{
			speed_right = 0;
		}
		
		// 电量递减模拟耗电，低于20重置为100
		battery--;
		if(battery < 20)
		{
			battery = 100;
		}
		
		// 模式0~8循环
		mode++;
		if(mode > 8)
		{
			mode = 0;
		}
		
		// 设备状态0~5循环
		state++;
		if(state > 5)
		{
			state = 0;
		}
		
		// 自定义协议三路数据独立变化
		data1++;
		if(data1 > 20) data1 = 1;
		
		data2 += 3;
		if(data2 > 50) data2 = 2;
		
		data3++;
		if(data3 > 30) data3 = 3;
		
		//==================== 组装并发送串口数据包 ====================
		// 摇杆协议：0X55 0X5A 速度、角度、速度、电量、模式、状态 0X5B
		uint8_t arr1[] = {0X55,0X5A, speed_left, gyro_angle, speed_right, battery, mode, state, 0X5B};
		My_USART3_SendArray(arr1, 9);
		
		// 自定义协议：0X2C 0X12 三路数据 0X5B
		uint8_t arr2[] = {0X2C,0X12, data1, data2, data3, 0X5B};
		My_USART3_SendArray(arr2, 6);
		
		Delay_ms(200);
		
		// OLED刷新
		OLED_ShowHexNum(1, 4, My_USART3_Data, 2);
	}
}