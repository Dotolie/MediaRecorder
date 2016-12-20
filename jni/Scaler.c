#include <pthread.h>
#include <stdio.h>
#include <poll.h>
#include <unistd.h>
#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <jni.h>
#include <android/log.h>
#include "Scaler.h"

//#define DEBUG_LOG
#ifdef DEBUG_LOG
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)
#else
#define LOGI(fmt, args...)
#define LOGD(fmt, args...)
#define LOGE(fmt, args...)
#endif

static const char *TAG="JNI_Scaler";

static int g_fd = -1;
static int g_fd325 = -1;
static int g_fd6124 = -1;
static int g_fd_irq = -1;
static int g_nRunning = 0;
static pthread_t g_irqThread;
static JNIEnv *g_env;
static JavaVM *g_jvm;
static jclass g_clazz = NULL;
static jmethodID g_methodID = NULL;



int gpio_is_exported(size_t gpio)
{
	int fd = 0;
	int i;
	char buf[64] = {0};
	size_t len = 0;

	len = snprintf(buf, sizeof(buf), CFG_GPIO_DIR "/gpio%lu/edge", (unsigned long)gpio);
	fd = open(buf, O_WRONLY);
	LOGD("[%s] --fd=%d\r\n", __FUNCTION__, fd);
	if (fd >= 0) {
		close(fd);
		//OK, GPIO is exported
		return 0;
	}

	return -1;
}


int gpio_export(size_t gpio)
{
	int fd = 0;
	int i;
	char buf[64] = {0};
	size_t len = 0;

	if( gpio_is_exported(gpio) == 0 )
	{
		return 0; //No need to re-export
	}

	fd = open(CFG_GPIO_DIR "/export", O_WRONLY);
	if( fd < 0 )
	{
		return -1;
	}

	len = snprintf(buf, sizeof(buf), "%lu", (unsigned long)gpio);
	write(fd, buf, len);
	close(fd);

	LOGD("[%s] --gpio=%d\r\n", __FUNCTION__, gpio);

	/* wait until file is actually available in user space */
	for (i = 0; i < CFG_GPIO_SYS_FILE_EXPORTED_TIME_IN_100MS; i++)
	{
		if( gpio_is_exported(gpio) == 0 )
		{
			return 0; //GPIO is present in user space
		}
		usleep(100 * 1000);
	}

	return -1;
}

int gpio_unexport(size_t gpio)
{
	int fd = 0;
	char buf[64] = {0};
	size_t len = 0;

	fd = open(CFG_GPIO_DIR "/unexport", O_WRONLY);
	if( fd < 0 )
	{
		return -1;
	}

	len = snprintf(buf, sizeof(buf), "%lu", (unsigned long)gpio);
	write(fd, buf, len);
	close(fd);

	LOGD("[%s] --gpio=%d\r\n", __FUNCTION__, gpio);

	return 0;
}

int gpio_set_direction(size_t gpio, int output)
{
	int fd = 0;
	char buf[64] = {0};
	size_t len = 0;

	len = snprintf(buf, sizeof(buf), CFG_GPIO_DIR "/gpio%lu/direction", (unsigned long)gpio);

	fd = open(buf, O_WRONLY);
	if( fd < 0 )
	{
		return -1;
	}

	if(output)
	{
		write(fd, "out", 3);
	}
	else
	{
		write(fd, "in", 2);
	}

	close(fd);

	LOGD("[%s] --len=%d\r\n", __FUNCTION__, len);

	return 0;
}

int gpio_set_edge(size_t gpio, int rising, int falling)
{
	int fd = 0;
	char buf[64] = {0};
	size_t len = 0;

	len = snprintf(buf, sizeof(buf), CFG_GPIO_DIR "/gpio%lu/edge", (unsigned long)gpio);

	fd = open(buf, O_WRONLY);
	if( fd < 0 )
	{
		return -1;
	}

	if(rising && falling)
	{
		write(fd, "both", 4);
	}
	else if(rising)
	{
		write(fd, "rising", 6);
	}
	else
	{
		write(fd, "falling", 7);
	}

	close(fd);

	LOGD("[%s] --gpio=%d, rising=%d, falling=%d\r\n", __FUNCTION__, gpio, rising, falling);
	return 0;
}

int gpio_open(size_t gpio, int mode)
{
	int fd = 0;
	char buf[64] = {0};
	size_t len = 0;

	len = snprintf(buf, sizeof(buf), CFG_GPIO_DIR "/gpio%lu/value", (unsigned long)gpio);

	fd = open(buf, mode | O_NONBLOCK);
	if( fd < 0 )
	{
		return -1;
	}

	LOGD("[%s] --fd=%d\r\n", __FUNCTION__, fd);

	return fd;
}

void Cleanup_Interface_Link(void)
{
	int ret = 0;

	if( g_fd_irq >= 0 ) {
		close(g_fd_irq);
		g_fd_irq = -1;
	}
	//Unexport all
	ret = gpio_unexport(PIN_IRQ);

	LOGD("[%s] --ret=%d\r\n", __FUNCTION__, ret);
}

int Set_Interface_Link(void)
{
	int ret = 0;

	ret = gpio_export(PIN_IRQ);
	LOGD("[%s] --ret=%d\r\n", __FUNCTION__, ret);

	if(ret)
	{
		Cleanup_Interface_Link();
		return -1;
	}

	g_fd_irq = gpio_open(PIN_IRQ, O_RDONLY);
	if(g_fd_irq < 0)
	{
		Cleanup_Interface_Link();
		return -1;
	}

	LOGD("[%s] --g_fd_irq=%d\r\n", __FUNCTION__, g_fd_irq);

	return 0;
}

void* irq_pin_helper(void* param)
{
	int ret = 0;
	struct pollfd pollfd;
	char c = 0;
	int status = 0;

	if((*g_jvm)->AttachCurrentThread(g_jvm, &g_env, NULL)!=JNI_OK) {
		LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
		return NULL;
	}

	pollfd.fd = g_fd_irq;
	pollfd.events = POLLPRI  ;
	pollfd.revents = 0;


	g_nRunning = 1;
	//Initial status: If pin is already high, post an event
	read(g_fd_irq, &c, 1);
	lseek(g_fd_irq, 0, SEEK_SET);
	LOGD("[%s] ---------------- c=%c (0x%x) [%c]\r\n", __FUNCTION__, c, c, '3');


	if( c == '1' )
	{
		if( g_fd6124 > 0 ) {
			int nTemp;
			ioctl( g_fd6124, IOC_VDEC_SET_MOTION_CLEAR, &nTemp );
		}
	}

	while(g_nRunning)
	{
		ret = poll( &pollfd, 1, 1000 ); //Block forever
//		LOGD("[%s] poll ret=%d\r\n", __FUNCTION__, ret);

		if( ret < 0  || pollfd.revents & POLLNVAL)
		{
			LOGD("[%s] poll.fd=%d poll.rev=0x%x, ret=%d----\r\n", __FUNCTION__, pollfd.fd, pollfd.revents, ret);
			return NULL; //Return if thread is joining
		}
		if( pollfd.revents & POLLPRI )
		{

			//Do a dummy read to acknowledge the event, before posting the event (to avoid race condition)
			read(g_fd_irq, &c, 1);
			lseek(g_fd_irq, 0, SEEK_SET);

			LOGD("[%s] evnet post--------------c=%c (0x%x)----\r\n", __FUNCTION__, c, c);

			(*g_env)->CallStaticVoidMethod(g_env, g_clazz, g_methodID);
		}

	}

	if((*g_jvm)->DetachCurrentThread(g_jvm)!=JNI_OK) {
		LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
		return NULL;
	}

	LOGD("[%s] end loop -------------\r\n", __FUNCTION__);
	return NULL;
}


int Set_Interrupt(void)
{
	//This function will start a thread that will call a callback function whenever IRQ pin drops
	int ret = 0;
    /* configure IRQ pin to react on rising edge only */
	ret = gpio_set_edge(PIN_IRQ, 1, 0);

	LOGD("[%s]-----ret=%d\r\n", __FUNCTION__, ret);
	if(ret)
	{
		return -1;
	}
    ret = pthread_create(&g_irqThread, NULL, irq_pin_helper, NULL);
    if(ret)
    {
        return -1;
    }
    ret = sched_yield();
    if(ret)
    {
        return -1;
    }
	return 0;
}

void Cleanup_Interrupt()
{
	g_nRunning = 0;
	pthread_join(g_irqThread, NULL);
	LOGD("[%s]-----stop thread\r\n", __FUNCTION__);
}


/*
 * Class:     com_mvtech_Scaler
 * Method:    open
 * Signature: (Ljava/lang/String;I)Ljava/io/FileDescriptor;
 */
JNIEXPORT jobject JNICALL Java_com_mvtech_devices_Scaler_open
  (JNIEnv *env, jclass thiz)
{
	jobject mFileDescriptor = NULL;

	if( g_nRunning ) {
		Cleanup_Interrupt();
	}

	g_env = env;
	(*env)->GetJavaVM(env, &g_jvm);
	g_clazz = (*g_env)->NewGlobalRef(g_env, thiz);

	g_methodID = (*g_env)->GetStaticMethodID(g_env, g_clazz, "onMotionEvent", "()V");
	if( g_methodID == NULL) {
		LOGE("%s: methodID is null", __FUNCTION__);
		goto err;
	}


	/* Opening device */
	g_fd325 = open("/dev/mdin325", O_RDWR);
	LOGD("open() g_fd352 = %d", g_fd325);
	if (g_fd325 == -1)
	{
		/* Throw an exception */
		LOGE("Cannot open mdin325 port");
		/* TODO: throw an exception */
		return NULL;
	}

	g_fd6124 = open("/dev/nvp6124b" , O_RDWR);
	LOGD("open() g_fd6124 = %d", g_fd6124);
	if (g_fd6124 == -1)
	{
		/* Throw an exception */
		LOGE("Cannot open nvp6124 port");
		/* TODO: throw an exception */
		return NULL;
	}


	/* Create a corresponding file descriptor */
	jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
	jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
	jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
	mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
	(*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint)g_fd325);


	if( Set_Interface_Link() != 0 ) {
		Cleanup_Interface_Link();

		if( g_fd325 > 0 ) {
			close(g_fd325);
			g_fd325 = -1;
		}
		if( g_fd6124 > 0 ) {
			close(g_fd6124);
			g_fd6124 = -1;
		}
		LOGD("[%s] open err\r\n", __func__);

		return NULL;
	}

err:
	return mFileDescriptor;
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    SetOSD
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_com_mvtech_devices_Scaler_SetOSD
  (JNIEnv *env, jclass thiz, jbyte cOnOff)
{
	LOGD("[%s] cOnOff=0x%x\r\n", __func__, cOnOff);
	if( g_fd325 > 0 ) {
		ioctl( g_fd325, MDIN_OSD_ONOFF, &cOnOff );
	}
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    SetDetect
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_com_mvtech_devices_Scaler_SetDetection
  (JNIEnv *env, jclass thiz, jbyte cOnOff)
{
	int mEnable = cOnOff;
	LOGD("[%s] cOnOff=0x%x\r\n", __func__, cOnOff);
	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_ENABLE_MOTION, &mEnable );
	}
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    SetSensitivity
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_mvtech_devices_Scaler_SetTSensitivity
  (JNIEnv *env, jclass thiz, jint nSens)
{
	LOGD("[%s] val=0x%x\r\n", __func__, nSens );
	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_SET_MOTION_SENS2, &nSens );
	}
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    SetSensitivity
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_mvtech_devices_Scaler_SetPSensitivity
  (JNIEnv *env, jclass thiz, jint nSens)
{
	jint nArSens[16];
	LOGD("[%s] val=0x%x\r\n", __func__, nSens );
	nArSens[0] = nSens;
	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_SET_MOTION_SENS, &nArSens );
	}
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    GetMotion
 * Signature: ()B
 */
JNIEXPORT jbyte JNICALL Java_com_mvtech_devices_Scaler_GetMotionSens
  (JNIEnv *env, jclass thiz)
{
	jint nMotion;
	jbyte cRet = 0;
	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_GET_MOTION_CH, &nMotion );
		cRet = (jbyte) nMotion;
	}

	LOGD("[%s] motions=0x%x\r\n", __func__, cRet);
	return cRet;
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    GetVideo
 * Signature: ()B
 */
JNIEXPORT jbyte JNICALL Java_com_mvtech_devices_Scaler_GetVideoInputs
  (JNIEnv *env, jclass thiz)
{
	jint nInput;
	jbyte cRet = 0;


	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_GET_VIDEO_LOSS, &nInput );
		cRet = (jbyte)nInput;
	}

	LOGD("[%s] Vinput=0x%x\r\n", __func__, cRet);
	return cRet;
}

/*
 * Class:     com_mvtech_Scaler
 * Method:    SetIrq
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_mvtech_devices_Scaler_SetEventEnable
  (JNIEnv *env, jclass thiz, jint nOnOff)
{
	LOGD("[%s] g_nRunning=%d, nOnOff=%d\r\n", __func__, g_nRunning, nOnOff );

	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_SET_MOTION_IRQ, &nOnOff );

		if( g_nRunning == 0 && nOnOff > 0 ) {
			Set_Interrupt();
		}
		else if( g_nRunning > 0 && nOnOff == 0 ){
			Cleanup_Interrupt();
		}
	}
}

/*
 * Class:     com_mvtech_devices_Scaler
 * Method:    SetMotionClear
 * Signature: (I)V
 */
JNIEXPORT jbyte JNICALL Java_com_mvtech_devices_Scaler_SetEventClear
  (JNIEnv *env, jclass thiz)
{
	jint nMotion;
	jbyte cRet = 0;

	if( g_fd6124 > 0 ) {
		ioctl( g_fd6124, IOC_VDEC_SET_MOTION_CLEAR, &nMotion );
		cRet = (jbyte)nMotion;
	}

	LOGD("[%s] motions=0x%x\r\n", __func__, cRet);
	return cRet;
}

/*
 * Class:     com_mvtech_devices_Scaler
 * Method:    open
 * Signature: (Ljava/lang/String;I)Ljava/io/FileDescriptor;
 */
JNIEXPORT void JNICALL Java_com_mvtech_devices_Scaler_close
  (JNIEnv *env, jclass thiz)
{
	if( g_clazz ) {
		(*g_env)->DeleteGlobalRef(g_env, g_clazz);
		g_clazz = NULL;
	}
	if( g_nRunning > 0 ) {
		LOGD("[%s] g_nRunning=%d\r\n", __func__, g_nRunning);
		Cleanup_Interrupt();
	}

	Cleanup_Interface_Link();

	if( g_fd325 > 0 ) {
		LOGD("[%s] g_fd325=0x%x\r\n", __func__, g_fd325);
		close(g_fd325);
		g_fd325 = -1;
	}
	if( g_fd6124 > 0 ) {
		LOGD("[%s] g_fd6124=0x%x\r\n", __func__, g_fd6124);
		close(g_fd6124);
		g_fd6124 = -1;
	}

	LOGD("[%s] closed\r\n", __func__);
}
