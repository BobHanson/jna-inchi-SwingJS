package com.sun.jna;

public class PointerType {

	Pointer addr;
	
	public PointerType(Pointer address) {
		this.addr = address;
	}

	public PointerType() {
	}
	
	public Pointer getPointer() {
		return addr;
	}


}
