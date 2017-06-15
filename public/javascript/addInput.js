var counter = 1;
var limit = 3;
function addInput(divName){
    if (counter == limit)  {
        alert("You have reached the limit of adding " + counter + " inputs");
    }
    else {
        var newdiv = document.createElement('div' + counter);
        newdiv.innerHTML ='<div style="float: left;" id="key_'+ counter +'"> <dl class=" " id="verifier1_'+ counter +'_key_field"><dl class=" " id="key1_'+ counter +'_bodyName_field"> <dt><label for="environmentalBody1_'+ counter +'_key">Key</label></dt> <dd> <input type="text" id="verifier1_'+ counter +'_key" name="environmentalBody1['+ counter +'].bodyName" value="" inputdivclass="col-sm-8" placeholder="" class="form-field-group"> </dd> </dl></div> ' +
            '              <div style="float: left;" id="value_'+ counter +'"> <dl class=" " id="key1_'+ counter +'_value_field"> <dt><label for="value1_'+ counter +'_amount">Value</label></dt> <dd> <input type="text" id="verifier1_'+ counter +'_value" name="verifier1_['+ counter +'].amount" value="" inputdivclass="col-sm-8" placeholder="" class="form-field-group"> </dd> </dl> </div>';
        document.getElementById("newField").appendChild(newdiv);
        counter++;
    }
}

function removeField(divName) {
    if (counter == 1){
        alert("One field is mandatory")
    }
    else {
        counter--;
        var nameElement = document.getElementById('key_' + counter + ''); // notice the change
        nameElement.parentNode.removeChild(nameElement);
        var amountElement = document.getElementById('value_' + counter + ''); // notice the change
        amountElement.parentNode.removeChild(amountElement);
    }


}