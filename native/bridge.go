package main

import "C"
import "fmt"

//export GoHelloWorld
func GoHelloWorld() *C.char {
	return C.CString("Manjaniq native bridge OK")
}

func main() {}
