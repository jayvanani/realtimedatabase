package com.example.realtimedatabase

data class UserModel(val id:String,var name:String,val image:String) {

    constructor() : this("","","")

}