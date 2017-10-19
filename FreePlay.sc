FreePlay {
	var gui, server;
	var oscdef;
	var userNotes;
	var btn;
	var instrument;
	var major, minor;
	var keys, curKey;
	var octaves, curOctave;
	var userPattern, tempo;
	var noteData, cur, midiNote;
	var curInstrument, note, keys, curRatio, scales, curScale;
	var instrumentNames, instrumentControls;
	var clock, userClock, pattern, bar, beat;
	var songs, curSong, curPlayingSong;
	var schedFunc, envView;
	var att, rel, dec, attCurve, relCurve, decCurve;
	var userGain, songGain, start, end;
	//var <isSustaining = false;

	*new {arg server, gui;
		^super.new.init(server, gui);
	}

	init {arg thisServer, thisGUI;
		note = 0;
		gui = thisGUI;
		server = thisServer;
		tempo = 120;
		curOctave = 4;
		curKey = 'C';
		curScale = \major;
		userGain = -6;
		start = 0;
		end = 1;
		instrument = Dictionary.new;
		keys = Dictionary.new;
		scales = Dictionary.new;
		songs = Dictionary.new;
		scales.putPairs([\major, Scale.major.degrees ++ 12, \minor, Scale.minor.degrees ++ 12]);
		scales.postln;
		keys.putPairs(['C', 0, 'C#', 1, 'Db', 1, 'D', 2, 'D#', 3, 'Eb', 3, 'E', 4, 'F', 5, 'F#', 6, 'Gb', 6, 'G', 7, 'G#', 8, 'Ab', 8, 'A', 9, 'A#', 10, 'Bb', 10, 'B', 11]);
		att = 0.1; rel = 0.5; dec = 0; attCurve = -1; relCurve = -1; decCurve = -1;
		this.makeDict;
		this.addSongs;
		//cur = Dictionary.with(*[\tempo, ->120, \instrument -> \Violin, \note -> 60]);
		this.initSynths;
		this.makeGUI;
	}

	addSongs {
		var makeSongBufs;

		makeSongBufs = {arg thisFile;
			var noteDict = Dictionary.new;
			var fileArray = thisFile.fileName.split($.);
			noteDict.putPairs([\Name, fileArray[0].asSymbol, \Buffer, Buffer.read(server, thisFile.fullPath), \Key, fileArray[1], \Scale, fileArray[2], \Tempo, fileArray[3]]);
			noteDict;
		};

		PathName(Platform.userAppSupportDir ++ "/downloaded-quarks/EncephalophoneGUI/Songs/").entries.do({arg thisEntry;
			songs.put(thisEntry.fileName.split($.)[0].asSymbol, makeSongBufs.value(thisEntry));
		});
		curSong = songs.asSortedArray[0][0];
		songs.postln;
		curSong.postln;
	}

	makeDict {
		var makeBufs, cond, loading;
		var list;
		list = SortedList.new;
		cond = Condition.new;


		makeBufs = {arg entries;
			var noteDict = Dictionary.new;
			var minimum = 128;
			var maximum = 0;
			var startView;

			entries.filesDo({arg thisFile;
				var fileArray = thisFile.fileName.split($.);
				var midiNum = fileArray[fileArray.size - 3].namemidi;
				minimum = min(midiNum, minimum);
				maximum = max(midiNum, maximum);
				noteDict.put(midiNum, Buffer.read(server, thisFile.fullPath/*,action:{cond.test_(true).signal}*/));
				//cond.wait;
				//cond.test_(false);
			});
			noteDict.put(\min, minimum);
			noteDict.put(\max, maximum);
			noteDict;
		};

		PathName(Platform.userAppSupportDir ++ "/downloaded-quarks/EncephalophoneGUI/Musical_Instruments_midi/").entries.do({arg thisEntry;
			list.add(thisEntry.folderName);
			instrument.put(thisEntry.folderName.asSymbol, makeBufs.value(thisEntry));
		});
		instrumentNames = list.asArray;
	}

	/*	isSustaining_ {arg bool;
	if(bool, {
	// nowPlayingSynth.set(\sustaining, 1);
	isSustaining = true;
	}, {
	// nowPlayingSynth.set(\gate, 0);
	this.releaseLast;
	isSustaining = false;
	})
	}*/

	makeGUI {
		var makeBox, makeView;
		var noteBox, controlBox, soundBox, backingBox, synthBox;
		var instrumentBox, nameArray, controlDict;
		var startView;
		var envKnobNames, envViews, envActions, envValues, envKnobActions, envKnobValues;
		var curveNames, curveViews, curveActions, curveValues, curveKnobActions, curveKnobValues;
		var sliderNames, sliderViews, sliderTextActions, sliderTextValues, sliderActions, sliderValues;
		var durationSlider, durationText;

		"Got to GUI".postln;
		controlDict = Dictionary.new;

		makeBox = {
			View().background_(Color.grey.alpha_(0.2));
		};

		noteBox = makeBox.value().fixedHeight_(gui.bounds.height - 300);
		controlBox = makeBox.value();
		instrumentBox = makeBox.value();
		backingBox = makeBox.value().fixedWidth_(275);
		synthBox = makeBox.value().fixedWidth_(300);

		userNotes = Array.fill(8, {
			var thisNote, thisView;
			thisNote = NoteVis(95, Color.white, thisView).animate;
			thisView = thisNote.getView;
			[thisNote, thisView];
		});

		userNotes = userNotes.flop;

		startView = View().background_(Color.black);
		gui.layout = VLayout (
			noteBox, HLayout(controlBox, synthBox, backingBox)
		);

		noteBox.layout_(HLayout(*userNotes[1]));

		btn = Button()
		.states_([
			["START", Color.white, Color.black],
			["STOP", Color.black, Color.white]
		])
		.font_(Font.new().pixelSize_(15))
		.action_({arg button;
			if(button.value == 1,
				{
					clock ?? {this.startClock; this.showInfo};
					this.startSequence;
					this.quantNotes;
				},
				{
					this.stopUserClock;
					this.stopSequence;
				}
			)
		});

		controlBox.layout = VLayout(btn, instrumentBox);


		instrumentControls = [
			[
				"Tempo",                                                   // name
				TextField(),                                               // control
				{arg field; (userClock !? {userClock.tempo_(field.value.asInteger/60)}); tempo =  field.value.asInteger}, // action
				120                                                          // valueAction
			],
			[
				"Instrument",
				PopUpMenu(),
				{arg field;	curInstrument = field.item.asSymbol;},
				instrumentNames[0],
				instrumentNames
			],
			[
				"Key",
				TextField(),
				{arg field;	curKey = field.value.asSymbol;},
				"C"
			],
			[
				"Octave",
				TextField(),
				{arg field;curOctave = field.value.asInteger;},
				3
			],
			[
				"Scale",
				PopUpMenu(),
				{arg field; curScale = field.item.asSymbol; curScale.postln},
				"major",
				scales.asSortedArray.flop[0];
			]
		].collect({arg thisData;
			var control, layout;
			control = thisData[1];
			thisData.postln;
			if (thisData.size > 4, {
				control.items_(thisData[4]);
			});
			control.action_(thisData[2]);
			control.valueAction_(thisData[3]);
			[control, HLayout(*[StaticText(instrumentBox).string_(thisData[0] + ": ").stringColor_(Color.white), control.fixedWidth_(100)]), thisData[0].asSymbol];
		});

		instrumentControls = instrumentControls.flop;
		instrumentControls.postln;

		instrumentBox.layout = VLayout(*instrumentControls[1]);

		envView = EnvelopeView()
		.drawLines_(true)
		.selectionColor_(Color.red)
		.thumbSize_(5);

		envKnobNames = ["att", "dec", "rel"].collect({arg thisString, index;
			StaticText().string_(thisString).font_(Font().pixelSize_(10)).stringColor_(Color.white).align_(\center)
		});

		envActions = [
			{arg field;	att = field.value.asFloat; controlDict[\att][1].value_(att); this.setEnvView()},
			{arg field;	dec = field.value.asFloat; controlDict[\dec][1].value_(dec.linlin(-96, 0, 0, 1)); this.setEnvView()},
			{arg field;	rel = field.value.asFloat; controlDict[\rel][1].value_(rel);  this.setEnvView()}
		];

		envValues = [0.1, -6, 0.6];

		envKnobActions = [
			{arg field;	att = field.value.asFloat; dec.postln; controlDict[\att][0].value_(att); this.setEnvView()},
			{arg field;	dec = field.value.asFloat.linlin(0, 1, -96, 0);  dec.postln; controlDict[\dec][0].value_(dec); this.setEnvView()},
			{arg field;	rel = field.value.asFloat.linlin(0, 1, 1, 0); controlDict[\rel][0].value_(rel); this.setEnvView()}
		];

		envViews = [
			envKnobNames,
			Array.fill(envKnobNames.size, {TextField().fixedWidth_(45)}),
			envActions,
			envValues,
			Array.fill(envKnobNames.size, {Knob().fixedWidth_(30).mode_(\vert)}),
			envKnobActions
		].flop.collect({arg thisData;
			var name = thisData[0].string.asSymbol;
			name.postln;
			controlDict.put(name, [thisData[1], thisData[4]]);
			controlDict[name][0].action_(thisData[2]);
			controlDict[name][1].action_(thisData[5]);
			controlDict[name][0].valueAction_(thisData[3]);
			VLayout([thisData[4], align: \center], [thisData[1], align: \center], thisData[0])
		});

		curveNames = [\attCurve, \decCurve, \relCurve];

		curveActions = [
			{arg field;	attCurve = field.value.asFloat; controlDict[\attCurve][1].value_(attCurve.linlin(-7, 7, 0, 1)); this.updateEnvView()},
			{arg field;	decCurve = field.value.asFloat; controlDict[\decCurve][1].value_(decCurve.linlin(-7, 7, 0, 1)); this.updateEnvView()},
			{arg field;	relCurve = field.value.asFloat; controlDict[\relCurve][1].value_(relCurve.linlin(-7, 7, 0, 1));  this.setEnvView()}
		];

		curveValues = [-1, -1, 1];

		curveKnobActions = [
			{arg field;	attCurve = field.value.asFloat.linlin(0, 1, -7, 7); dec.postln; controlDict[\attCurve][0].value_(attCurve); this.updateEnvView()},
			{arg field;	decCurve = field.value.asFloat.linlin(0, 1, -7, 7);  dec.postln; controlDict[\decCurve][0].value_(decCurve); this.updateEnvView()},
			{arg field;	relCurve = field.value.asFloat.linlin(0, 1, -7, 7); controlDict[\relCurve][0].value_(relCurve); this.updateEnvView()}
		];

		curveViews = [
			curveNames,
			Array.fill(curveNames.size, {TextField().fixedWidth_(35)}),
			curveActions,
			curveValues,
			Array.fill(curveNames.size, {Knob().fixedWidth_(30).mode_(\vert)}),
			curveKnobActions
		].flop.collect({arg thisData;
			var name = thisData[0];
			name.postln;
			controlDict.put(name, [thisData[1], thisData[4]]);
			controlDict[name][0].action_(thisData[2]);
			controlDict[name][1].action_(thisData[5]);
			controlDict[name][0].valueAction_(thisData[3]);
			VLayout([thisData[4], align: \center], [thisData[1], align: \center])
		});

		durationText = [["0", \left], ["duration", \center], ["1", \right]].collect({arg thisData;
			var text = StaticText().string_(thisData[0]).font_(Font().pixelSize_(10)).stringColor_(Color.white).align_(thisData[1]).fixedWidth_(60);
			controlDict.put(thisData[1], text);
			text;
		});

		durationSlider = RangeSlider()
		.orientation_(\horizontal)
		.action_({arg object;
			start = object.lo.round(0.01);
			end = object.hi.round(0.01);
			controlDict[\left].string_(object.lo.round(0.01));
			controlDict[\right].string_(object.hi.round(0.01));
		});

		synthBox.layout_(
			VLayout(
				envView,
				HLayout(
					HLayout(
						*envViews,
					),
					nil,
					VLayout(
						HLayout(*curveViews),
						StaticText().string_("curves").font_(Font().pixelSize_(10)).stringColor_(Color.white).align_(\center)
					)
				),
				HLayout(
					VLayout(
						durationSlider,
						HLayout(
							*durationText
						)
					)
				)
			)
		);

		sliderNames = ["user", "song"].collect({arg thisString, index;
			StaticText().string_(thisString).font_(Font().pixelSize_(12)).stringColor_(Color.white).align_(\center).fixedSize_(40)
		});

		sliderTextActions = [
			{arg field;	userGain = field.value.asFloat; controlDict[\user][1].value_(userGain.linlin(-96, 0, 0, 1)); this.updateEnvView()},
			{arg field;	songGain = field.value.asFloat; controlDict[\song][1].value_(songGain.linlin(-96, 0, 0, 1)); this.updateEnvView()},
		];

		sliderTextValues = [-3, -3];

		sliderActions = [
			{arg field;	userGain = field.value.linlin(0, 1, -96, 0);  dec.postln; controlDict[\user][0].value_(userGain.round(0.1)); this.updateEnvView()},
			{arg field;	songGain = field.value.linlin(0, 1, -96, 0); controlDict[\song][0].value_(songGain.round(0.1)); this.updateEnvView()},
		];

		sliderViews = [
			sliderNames,
			Array.fill(sliderNames.size, {TextField()}),
			sliderTextActions,
			sliderTextValues,
			Array.fill(sliderNames.size, {Slider().orientation_(\horizontal).fixedWidth_(140)}),
			sliderActions
		].flop.collect({arg thisData;
			var name = thisData[0].string.asSymbol;
			controlDict.put(name, [thisData[1], thisData[4]]);
			controlDict[name][0].action_(thisData[2]);
			controlDict[name][1].action_(thisData[5]);
			controlDict[name][0].valueAction_(thisData[3]);
			HLayout(thisData[0], [thisData[4].fixedHeight_(25)], [thisData[1]])
		});

		backingBox.layout = VLayout(
			VLayout(
				View().background_(Color.grey.alpha_(0.2)).layout_(
					VLayout(
						*sliderViews
					)
				),
				HLayout(
					StaticText().string_("Song: ").font_(Font().pixelSize_(15)).stringColor_(Color.white),
					PopUpMenu().items_(songs.asSortedArray.flop[0]).fixedWidth_(200)
				),
				nil,

				Button().states_([
					["START SONG", Color.white, Color.black],
					["STOP SONG", Color.black, Color.white]
				]).action_({arg button;
					if(button.value == 1,
						{
							this.startSong
						},
						{
							this.stopSong
						}
					)
				});
			)
		);
	}



	initSynths {
		SynthDef.new(\playSound,
			{arg buffer, gain = -6, thisAtt = 0.05, thisDec = 0.2, thisRel = 0.3, thisAttCurve = 0.05, thisDecCurve = 0.2, thisRelCurve = 0.3, thisStart = 0, thisEnd = 1, ratio = 1, midi = 50;
				var in, out, env, amp;
				var startPos, endPos;
				amp = gain.dbamp;
				thisDec = thisDec.dbamp;
				startPos = thisStart * BufDur.kr(buffer);

				in = PlayBuf.ar(1, buffer, BufRateScale.kr(buffer) * ratio, startPos: (startPos * server.sampleRate));

				out = in!2;
				out = out *  EnvGen.kr(Env([0, amp, amp * thisDec, 0], [thisAtt, 1.0 - (thisAtt + rel), thisRel], [thisAttCurve, thisDecCurve, thisRelCurve]),  timeScale: (BufDur.kr(buffer) * thisEnd) - startPos, doneAction: 2);
				Out.ar(0, out);
		}).add;

		SynthDef.new(\playSong,
			{arg buffer, gain = -6, turnOff = 1;
				var in, out, env, amp;
				amp = gain.dbamp;
				//env = EnvGen.kr(Env.adsr(a, d, s, r), levelScale: amp, timeScale: dur, doneAction: 2);
				in = PlayBuf.ar(2, buffer, BufRateScale.kr(buffer));

				out = in * turnOff;
				DetectSilence.ar(out, doneAction: 2);
				out = out * amp;
				Out.ar(0, out);
		}).add;

	}

	startSequence {
		oscdef = OSCdef(
			\playOsc,
			{ |msg, time, addr|
				msg.postln;
				note = msg[1] - 1;
				this.setRatio();
			},
			'/fred'
		)
	}

	startClock {
		clock = TempoClock.new(tempo/60);
	}

	startSong {
		var playSong, startUser;
		instrumentControls[2].do({arg thisSymbol, index;
			if (songs[curSong].includesKey(thisSymbol), {
				if (thisSymbol != \Scale, {
					instrumentControls[0][index].valueAction_(songs[curSong][thisSymbol]);
				}, {
					instrumentControls[0][index].valueAction_(scales.asSortedArray.flop[0].indexOf(songs[curSong][thisSymbol].asSymbol));
				});
			})
		});

		if (btn.value == 1, {
			btn.valueAction_(0);
		});

		this.stopClock;
		this.startClock;
		this.showInfo;

		playSong = {
			curPlayingSong = Synth(\playSong, [\buffer: songs[curSong][\Buffer]]);
			NodeWatcher(server).register(curPlayingSong);
		};

		startUser = {
			{btn.valueAction_(1)}.defer;
		};

		clock.schedAbs(clock.nextBar, playSong);
		clock.schedAbs(8, startUser);
	}

	stopSong {
		btn.valueAction_(0);
		curPlayingSong.free;
		this.stopClock;
	}

	showInfo {
		var postInfo;
		postInfo = {
			//userNotes.postln;
			("beats :" + (clock.beats.floor%clock.beatsPerBar + 1)).postln;
			("bar :" + (clock.bar + 1)).postln;
			"".postln;
			//note.postln;
			1
		};
		clock.schedAbs(clock.nextBar, postInfo);
	}

	scheduleNotes {
		userClock ?? {userClock = TempoClock.new(tempo/60)};
		schedFunc = {
			{userNotes[0][note].animate}.defer;
			//Synth(\playSound, [\buffer: instrument[curInstrument][midiNote], \ratio, curRatio]);
		Synth(\playSound, [\buffer: instrument[curInstrument][midiNote], \ratio, curRatio, \thisAtt, att, \thisDec, dec, \thisRel, rel, \thisAttCurve, attCurve, \thisDecCurve, decCurve, \thisRelCurve, relCurve, \thisStart, start, \thisEnd, end, \gain, userGain, \midi, midiNote]);
			1
		};
		userClock.schedAbs(userClock.nextTimeOnGrid, schedFunc);
	}

	quantNotes {
		clock !? {clock.schedAbs(clock.beats.floor + 1, {this.scheduleNotes})};
	}

	stopClock {
		clock !? {clock.clear};
		clock = nil;
		clock.postln;
	}

	stopUserClock {
		userClock.clear;
		userClock = nil;
		userClock.postln;
	}

	stopSequence {
		oscdef.free;
	}

	getOscDef {
		^oscdef;
	}

	getBtn {
		^btn;
	}

	setEnvView {
		if (att < 0.01, {
			att = 0.01;
		});
		if(rel < 0.01, {
			rel = 0.01
		});
		if ((att + rel) > 1.0, {
			if (att > rel, {
				rel = 1.0 - att;

			}, {
				att = 1.0 - rel;
			})
		});
		if (dec > 0,
			{dec = 0}
		);
		this.updateEnvView();
	}

	updateEnvView {
		envView.setEnv(Env([0, userGain.dbamp, userGain.dbamp * dec.dbamp, 0], [att, 1.0 - (att + rel), rel], [attCurve, decCurve, relCurve]));
	}

	setRatio {
		var rout, thisBuf, min, max, midiDif, major, thisNote;
		thisNote = keys[curKey] + scales[curScale][note] + (12 * curOctave) + 24;
		min = instrument[curInstrument][\min];
		max = instrument[curInstrument][\max];
		midiDif = 0;
		case
		{thisNote < min} {
			midiDif = (thisNote - min);
			thisNote = min;
		}
		{thisNote > max} {
			midiDif = (thisNote - max);
			thisNote = max;
		};
		curRatio = midiDif.midiratio;
		midiNote = thisNote;
		curRatio.postln;
		instrument[curInstrument][thisNote].postln;
	}
}