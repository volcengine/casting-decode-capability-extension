// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

import UIKit

class MainViewController: UIViewController {
    
    private var statusLabel: UILabel!
    private var devicesTextView: UITextView!
    private var searchButton: UIButton!
    private var createDeviceButton: UIButton!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        view.backgroundColor = .white
        title = "UPnP Demo"
        
        let stackView = UIStackView()
        stackView.axis = .vertical
        stackView.spacing = 16
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            stackView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
        ])
        
        statusLabel = UILabel()
        statusLabel.text = "Status"
        statusLabel.font = UIFont.boldSystemFont(ofSize: 18)
        stackView.addArrangedSubview(statusLabel)
        
        let statusValueLabel = UILabel()
        statusValueLabel.text = "Ready"
        statusValueLabel.numberOfLines = 0
        statusValueLabel.backgroundColor = UIColor(red: 0.88, green: 0.88, blue: 0.88, alpha: 1.0)
        statusValueLabel.layer.cornerRadius = 4
        statusValueLabel.clipsToBounds = true
        statusValueLabel.translatesAutoresizingMaskIntoConstraints = false
        stackView.addArrangedSubview(statusValueLabel)
        statusValueLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 40).isActive = true
        
        searchButton = UIButton(type: .system)
        searchButton.setTitle("Search Devices", for: .normal)
        searchButton.addTarget(self, action: #selector(searchForDevices), for: .touchUpInside)
        searchButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        searchButton.backgroundColor = .systemBlue
        searchButton.setTitleColor(.white, for: .normal)
        searchButton.layer.cornerRadius = 8
        searchButton.translatesAutoresizingMaskIntoConstraints = false
        stackView.addArrangedSubview(searchButton)
        searchButton.heightAnchor.constraint(equalToConstant: 44).isActive = true
        
        createDeviceButton = UIButton(type: .system)
        createDeviceButton.setTitle("Create Device", for: .normal)
        createDeviceButton.addTarget(self, action: #selector(createDevice), for: .touchUpInside)
        createDeviceButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        createDeviceButton.backgroundColor = .systemGreen
        createDeviceButton.setTitleColor(.white, for: .normal)
        createDeviceButton.layer.cornerRadius = 8
        createDeviceButton.translatesAutoresizingMaskIntoConstraints = false
        stackView.addArrangedSubview(createDeviceButton)
        createDeviceButton.heightAnchor.constraint(equalToConstant: 44).isActive = true
        
        let devicesLabel = UILabel()
        devicesLabel.text = "Discovered Devices"
        devicesLabel.font = UIFont.boldSystemFont(ofSize: 18)
        stackView.addArrangedSubview(devicesLabel)
        
        devicesTextView = UITextView()
        devicesTextView.text = "No devices found yet"
        devicesTextView.isEditable = false
        devicesTextView.backgroundColor = UIColor(red: 0.96, green: 0.96, blue: 0.96, alpha: 1.0)
        devicesTextView.layer.cornerRadius = 4
        devicesTextView.translatesAutoresizingMaskIntoConstraints = false
        stackView.addArrangedSubview(devicesTextView)
        devicesTextView.heightAnchor.constraint(greaterThanOrEqualToConstant: 200).isActive = true
    }
    
    @objc private func searchForDevices() {
        statusLabel.text = "Searching for UPnP devices..."
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            let mockDevices = """
                Found device: Test Media Renderer
                  Device Type: urn:schemas-upnp-org:device:MediaRenderer:1
                  Location: http://192.168.1.100:1900/desc.xml
                
                Found device: Test Media Server
                  Device Type: urn:schemas-upnp-org:device:MediaServer:1
                  Location: http://192.168.1.101:1901/desc.xml
            """
            self?.devicesTextView.text = mockDevices
            self?.statusLabel.text = "Search completed"
        }
    }
    
    @objc private func createDevice() {
        statusLabel.text = "Creating UPnP device..."
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            self?.statusLabel.text = "Device created successfully: uuid:test-media-renderer-123456"
        }
    }
}
