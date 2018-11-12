function encode(){
  var string = document.getElementById("template").value;
  var encodeData = Base64.encode(string);
  document.getElementById("template").value = encodeData;
  console.log(document.getElementById("template").value);
  document.getElementById("templateId").submit();
}

function onSignIn(googleUser){

  var id_token = googleUser.getBasicProfile().getEmail();<!--.getAuthResponse().id_token; -->

  console.log(id_token);
  setEmail(id_token);
  submit();

}

function setEmail(email){
  document.getElementById("token").value = email;
}

function submit() {
  document.LoginForm.submit();
}

function signOut(){
  var auth2 = gapi.auth2.getAuthInstance();
  auth2.signOut().then(function () {
    console.log('User signed out.');
  });
}

function colorFunction(value){
  var str = "isLive";
  var color = document.getElementById(str.concat(value));

  console.log = color.options[color.selectedIndex].value
  if(color.options[color.selectedIndex].value === "LIVE"){
    document.getElementById(value).style.backgroundColor = "#ff0000";
  } else {
    document.getElementById(value).style.backgroundColor = "#fff";
  }
}

function changeId(){
  var elemArn = document.getElementById('database_arn');
  var selectedValue = elemArn.options[elemArn.selectedIndex].value;

  var y = document.getElementById('arn');

  if(selectedValue === "ARN"){
    y.name = "arn";
  }
  else if (selectedValue === "Business Users"){
    y.name = "registration";
  }
}

function changeName(){
  var elem = document.getElementById('criteria');
  var selectedValue = elem.options[elem.selectedIndex].value;

  var x = document.getElementById('groupid');

  if(selectedValue === "groupid"){
    x.name = "groupid";
  }else if (selectedValue === 'regime'){
    x.name = "regime";
  }
}