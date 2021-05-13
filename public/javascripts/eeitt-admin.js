function encode() {
  const string = document.getElementById("template").value;
  const encodeData = Base64.encode(string);
  document.getElementById("template").value = encodeData;
}

function onSignIn(googleUser) {
  const email = googleUser.getBasicProfile().getEmail();
  const name = googleUser.getBasicProfile().getName();
  setName(name);
  setEmail(email);
  submit();
}

function setName(name) {
  document.getElementById("username").value = name;
}

function setEmail(email) {
  document.getElementById("email").value = email;
}

function submit() {
  document.LoginForm.submit();
}

function signOut() {
  const auth2 = gapi.auth2.getAuthInstance();
  auth2.signOut().then(function() {
    console.log("User signed out.");
  });
}

function colorFunction(value) {
  const str = "isLive";
  const color = document.getElementById(str.concat(value));

  console.log = color.options[color.selectedIndex].value;
  if (color.options[color.selectedIndex].value === "LIVE") {
    document.getElementById(value).style.backgroundColor = "#ff0000";
  } else {
    document.getElementById(value).style.backgroundColor = "#fff";
  }
}

function changeId() {
  const elemArn = document.getElementById("database_arn");
  const selectedValue = elemArn.options[elemArn.selectedIndex].value;

  const y = document.getElementById("arn");

  if (selectedValue === "ARN") {
    y.name = "arn";
  } else if (selectedValue === "Business Users") {
    y.name = "registration";
  }
}

function changeName() {
  const elem = document.getElementById("criteria");
  const selectedValue = elem.options[elem.selectedIndex].value;

  const x = document.getElementById("groupid");

  if (selectedValue === "groupid") {
    x.name = "groupid";
  } else if (selectedValue === "regime") {
    x.name = "regime";
  }
}
