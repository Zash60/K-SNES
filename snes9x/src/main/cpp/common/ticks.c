#include <time.h>

static struct timespec startTicks;

void ticksInitialize(void)
{
	clock_gettime(CLOCK_MONOTONIC, &startTicks);
}

unsigned int ticksGetTicks(void)
{
	struct timespec now;
	clock_gettime(CLOCK_MONOTONIC, &now);
	return (now.tv_sec - startTicks.tv_sec) * 1000 +
			(now.tv_nsec - startTicks.tv_nsec) / 1000000;
}
