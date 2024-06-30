#include <stdio.h>
#include <stdlib.h>

extern void Tiger_main();

void *Tiger_new(long size, void *vtable){
    void **ptr = (void **)malloc(size);
    *ptr = vtable;
    return (void *)ptr;
}

void *Tiger_getVirtualMethod(long *ptr, long vtableOffset, long methodOffset){
    long *vtable = (long *)ptr[vtableOffset];
    long *method = (long *)vtable[methodOffset];
    return method;
}

void Tiger_print(long n){
    printf("%ld\n", n);
}

void Tiger_debug(char *s){
    printf("%s\n", s);
}

void *Tiger_newArray(long length, long init){
    long *array = (long *)malloc((length + 1) * sizeof(long));
    array[0] = length;
    for(long i = 0; i < length; i++){
        array[i + 1] = init;
    }
    return (void *)array;
}

void *Tiger_arrayLength(long *array){
    long *length = (long *)array[0];
    return length;
}

void *Tiger_arraySelect(long *array, long index){
    long *length = (long *)array[0];
    if(index < 0 || index >= *length){
        printf("Array index out of bounds\n");
        exit(1);
    }
    return (void *)array[index + 1];
}


int main(){
    Tiger_main();
    return 0;
}

