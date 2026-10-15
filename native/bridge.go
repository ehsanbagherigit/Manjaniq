package main

import "C"

//export GoHelloWorld
func GoHelloWorld() *C.char {
	return C.CString("Manjaniq native bridge OK")
}

func main() {}
