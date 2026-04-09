-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 03:02 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `system_audit_history`
--

CREATE TABLE `system_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `system_audit_history`
--

INSERT INTO `system_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(1, 57, 'System', 'Details', 'Updated', 'Short Name', 'Core System', 'Core System1', 'Alice Smith', '2025-10-27 12:35:22', '2025-10-27 12:35:22'),
(1, 58, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 12:35:22', '2025-10-27 12:35:22'),
(1, 59, 'System', 'Details', 'Updated', 'Type', 'Database', 'Application', 'Alice Smith', '2025-10-27 12:35:22', '2025-10-27 12:35:22'),
(1, 60, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 12:36:53', '2025-10-27 12:36:53'),
(1, 162, 'System', 'Details', 'Updated', 'Confidentiality', NULL, '2', 'Alice Smith', '2025-11-02 16:49:47', '2025-11-02 16:49:47'),
(1, 163, 'System', 'Details', 'Updated', 'Integrity', NULL, '3', 'Alice Smith', '2025-11-02 16:49:47', '2025-11-02 16:49:47'),
(1, 164, 'System', 'Details', 'Updated', 'Availability', NULL, '1', 'Alice Smith', '2025-11-02 16:49:47', '2025-11-02 16:49:47'),
(3, 158, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'System', '2025-11-02 15:40:52', '2025-11-02 15:40:52'),
(3, 159, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'System', '2025-11-02 15:40:52', '2025-11-02 15:40:52'),
(3, 160, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'System', '2025-11-02 15:40:52', '2025-11-02 15:40:52'),
(3, 161, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Active', 'Yes', 'System', '2025-11-02 15:40:55', '2025-11-02 15:40:55'),
(20, 65, 'System', 'Details', 'Updated', 'Automatically Control Local Rules', 'No', NULL, 'Alice Smith', '2025-10-27 12:39:51', '2025-10-27 12:39:51'),
(20, 66, 'System', 'Details', 'Updated', 'Type', NULL, 'Application', 'Alice Smith', '2025-10-27 12:39:51', '2025-10-27 12:39:51'),
(27, 61, 'System', 'Details', 'Updated', 'Short Name', 'Test system', 'Test system1', 'Alice Smith', '2025-10-27 12:37:30', '2025-10-27 12:37:30'),
(27, 62, 'System', 'Details', 'Updated', 'Long Name', NULL, 'w', 'Alice Smith', '2025-10-27 12:37:30', '2025-10-27 12:37:30'),
(27, 63, 'System', 'Details', 'Updated', 'Automatically Control Local Rules', 'No', NULL, 'Alice Smith', '2025-10-27 12:37:30', '2025-10-27 12:37:30'),
(27, 64, 'System', 'Details', 'Updated', 'Type', NULL, 'Application', 'Alice Smith', '2025-10-27 12:37:30', '2025-10-27 12:37:30'),
(36, 109, 'System', 'Details', 'Updated', 'Parent Short Name', NULL, 'Core System1', 'Alice Smith', '2025-10-30 14:49:30', '2025-10-30 14:49:30'),
(36, 110, 'System', 'Details', 'Updated', 'Confidentiality', NULL, '5', 'Alice Smith', '2025-10-30 14:51:08', '2025-10-30 14:51:08'),
(45, 107, 'System', 'Details', 'Status Change', 'Axon Status', 'Pending', 'Active', 'Alice Smith', '2025-10-29 10:15:33', '2025-10-29 10:15:33'),
(45, 108, 'System', 'Details', 'Updated', 'Confidentiality', '3', NULL, 'Alice Smith', '2025-10-29 10:15:33', '2025-10-29 10:15:33'),
(53, 13, 'System', 'Details', 'Added', 'Short Name', NULL, 'ahned', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 14, 'System', 'Details', 'Added', 'Description', NULL, 'dfgg', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 15, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 16, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 17, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 18, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 19, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 20, 'System', 'Details', 'Added', 'Type', NULL, 'Service', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 21, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 22, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-10-22 13:44:39', '2025-10-22 13:44:39'),
(53, 23, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-22 13:44:40', '2025-10-22 13:44:40'),
(53, 24, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-22 13:44:40', '2025-10-22 13:44:40'),
(56, 25, 'System', 'Details', 'Added', 'Short Name', NULL, 'gdfgdf', 'esraa Johnson', '2025-10-23 13:25:37', '2025-10-23 13:25:37'),
(56, 26, 'System', 'Details', 'Added', 'Description', NULL, 'dfgdf', 'esraa Johnson', '2025-10-23 13:25:37', '2025-10-23 13:25:37'),
(56, 27, 'System', 'Details', 'Added', 'External', NULL, 'No', 'esraa Johnson', '2025-10-23 13:25:37', '2025-10-23 13:25:37'),
(56, 28, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'esraa Johnson', '2025-10-23 13:25:37', '2025-10-23 13:25:37'),
(56, 29, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 30, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 31, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 32, 'System', 'Details', 'Added', 'Type', NULL, 'Database', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 33, 'System', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 34, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 35, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(56, 36, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:25:38', '2025-10-23 13:25:38'),
(57, 37, 'System', 'Details', 'Added', 'Short Name', NULL, 'hj', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 38, 'System', 'Details', 'Added', 'Description', NULL, 'ghj', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 39, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 40, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 41, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 42, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 43, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 44, 'System', 'Details', 'Added', 'Type', NULL, 'Service', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 45, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 46, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 47, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 48, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:32:18', '2025-10-23 16:32:18'),
(57, 49, 'System', 'Details', 'Updated', 'Short Name', 'hj', 'hj2', 'Alice Smith', '2025-10-27 10:54:13', '2025-10-27 10:54:13'),
(57, 50, 'System', 'Details', 'Updated', 'External', 'No', 'No', 'Alice Smith', '2025-10-27 11:15:02', '2025-10-27 11:15:02'),
(57, 51, 'System', 'Details', 'Updated', 'Type', 'Application', 'Service', 'Alice Smith', '2025-10-27 11:15:02', '2025-10-27 11:15:02'),
(57, 52, 'System', 'Details', 'Updated', 'Confidentiality', '2', '5', 'Alice Smith', '2025-10-27 11:15:02', '2025-10-27 11:15:02'),
(57, 53, 'System', 'Details', 'Updated', 'External', 'No', 'No', 'Alice Smith', '2025-10-27 11:16:43', '2025-10-27 11:16:43'),
(57, 54, 'System', 'Details', 'Updated', 'Short Name', 'hj2', 'hj211', 'Alice Smith', '2025-10-27 11:41:01', '2025-10-27 11:41:01'),
(57, 55, 'System', 'Details', 'Updated', 'Type', 'Service', 'Application', 'Alice Smith', '2025-10-27 11:41:01', '2025-10-27 11:41:01'),
(57, 56, 'System', 'Details', 'Updated', 'Confidentiality', '5', NULL, 'Alice Smith', '2025-10-27 11:41:01', '2025-10-27 11:41:01'),
(57, 67, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:02:19', '2025-10-27 13:02:19'),
(57, 68, 'System', 'Details', 'Updated', 'Type', 'Application', 'Infrastructure', 'Alice Smith', '2025-10-27 13:02:19', '2025-10-27 13:02:19'),
(57, 69, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:10:55', '2025-10-27 13:10:55'),
(57, 70, 'System', 'Details', 'Updated', 'Automatically Control Local Rules', NULL, 'Yes', 'Alice Smith', '2025-10-27 13:10:55', '2025-10-27 13:10:55'),
(57, 71, 'System', 'Details', 'Updated', 'Type', 'Infrastructure', 'Application', 'Alice Smith', '2025-10-27 13:10:55', '2025-10-27 13:10:55'),
(57, 72, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:11:09', '2025-10-27 13:11:09'),
(57, 73, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:12:09', '2025-10-27 13:12:09'),
(57, 74, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:14:30', '2025-10-27 13:14:30'),
(57, 75, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:15:01', '2025-10-27 13:15:01'),
(57, 76, 'System', 'Details', 'Updated', 'Type', 'Application', 'Database', 'Alice Smith', '2025-10-27 13:27:24', '2025-10-27 13:27:24'),
(57, 77, 'System', 'Details', 'Updated', 'External', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:32:08', '2025-10-27 13:32:08'),
(57, 78, 'System', 'Details', 'Updated', 'Automatically Control Local Rules', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:32:08', '2025-10-27 13:32:08'),
(57, 79, 'System', 'Details', 'Updated', 'External', 'Yes', 'No', 'Alice Smith', '2025-10-27 13:32:24', '2025-10-27 13:32:24'),
(58, 80, 'System', 'Details', 'Added', 'Short Name', NULL, 'fsdf', 'Alice Smith', '2025-10-27 13:48:21', '2025-10-27 13:48:21'),
(58, 81, 'System', 'Details', 'Added', 'Description', NULL, 'sdf', 'Alice Smith', '2025-10-27 13:48:21', '2025-10-27 13:48:21'),
(58, 82, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-10-27 13:48:21', '2025-10-27 13:48:21'),
(58, 83, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'Alice Smith', '2025-10-27 13:48:21', '2025-10-27 13:48:21'),
(58, 84, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 85, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 86, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 87, 'System', 'Details', 'Added', 'Type', NULL, 'Application', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 88, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 89, 'System', 'Details', 'Added', 'Confidentiality', NULL, '1', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 90, 'System', 'Details', 'Added', 'Integrity', NULL, '3', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 91, 'System', 'Details', 'Added', 'Availability', NULL, '2', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 92, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 93, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 94, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 13:48:22', '2025-10-27 13:48:22'),
(58, 95, 'System', 'Details', 'Updated', 'Automatically Control Local Rules', 'No', 'Yes', 'Alice Smith', '2025-10-27 13:48:37', '2025-10-27 13:48:37'),
(58, 96, 'System', 'Details', 'Updated', 'Confidentiality', '1', NULL, 'Alice Smith', '2025-10-27 13:48:37', '2025-10-27 13:48:37'),
(58, 97, 'System', 'Details', 'Updated', 'Integrity', '3', NULL, 'Alice Smith', '2025-10-27 13:48:37', '2025-10-27 13:48:37'),
(58, 98, 'System', 'Details', 'Updated', 'Availability', '2', NULL, 'Alice Smith', '2025-10-27 13:48:37', '2025-10-27 13:48:37'),
(58, 99, 'System', 'Details', 'Updated', 'Automatically Control Local Rules', 'Yes', 'No', 'Alice Smith', '2025-10-27 13:53:09', '2025-10-27 13:53:09'),
(58, 100, 'System', 'Details', 'Updated', 'Confidentiality', NULL, '4', 'Alice Smith', '2025-10-27 14:06:01', '2025-10-27 14:06:01'),
(58, 101, 'System', 'Details', 'Updated', 'Integrity', NULL, '4', 'Alice Smith', '2025-10-27 14:06:01', '2025-10-27 14:06:01'),
(58, 102, 'System', 'Details', 'Updated', 'Availability', NULL, '4', 'Alice Smith', '2025-10-27 14:06:01', '2025-10-27 14:06:01'),
(58, 103, 'System', 'Details', 'Updated', 'Type', 'Application', 'Infrastructure', 'Alice Smith', '2025-10-27 14:06:08', '2025-10-27 14:06:08'),
(58, 104, 'System', 'Details', 'Updated', 'Confidentiality', '4', '3', 'Alice Smith', '2025-10-27 14:06:17', '2025-10-27 14:06:17'),
(58, 105, 'System', 'Details', 'Updated', 'Integrity', '4', '2', 'Alice Smith', '2025-10-27 14:06:17', '2025-10-27 14:06:17'),
(58, 106, 'System', 'Details', 'Updated', 'Availability', '4', '1', 'Alice Smith', '2025-10-27 14:06:17', '2025-10-27 14:06:17'),
(58, 111, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'System', '2025-11-02 11:38:02', '2025-11-02 11:38:02'),
(58, 112, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Role', 'System', '2025-11-02 11:38:02', '2025-11-02 11:38:02'),
(58, 113, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-02 11:38:02', '2025-11-02 11:38:02'),
(58, 135, 'Stakeholder', 'edit', 'Updated', 'Name', 'esraa Johnson', 'Eve Davis', 'System', '2025-11-02 12:11:12', '2025-11-02 12:11:12'),
(58, 136, 'Stakeholder', 'edit', 'Updated', 'Role', 'System Role', 'System Steward', 'System', '2025-11-02 12:11:12', '2025-11-02 12:11:12'),
(58, 137, 'Stakeholder', 'edit', 'Updated', 'Role Status', 'Active', 'Not Active', 'System', '2025-11-02 12:11:27', '2025-11-02 12:11:27'),
(59, 114, 'System', 'Details', 'Added', 'Short Name', NULL, 'سيبسيب', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 115, 'System', 'Details', 'Added', 'Description', NULL, 'سيبسيب', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 116, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 117, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 118, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 119, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 120, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 121, 'System', 'Details', 'Added', 'Type', NULL, 'Database', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 122, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 123, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 124, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 125, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 12:07:09', '2025-11-02 12:07:09'),
(59, 126, 'Stakeholder', 'edit', 'Updated', 'Name', 'Alice Smith', 'esraa Johnson', 'System', '2025-11-02 12:07:32', '2025-11-02 12:07:32'),
(59, 127, 'Stakeholder', 'edit', 'Updated', 'Role', 'System Business Owner', 'System Role', 'System', '2025-11-02 12:07:32', '2025-11-02 12:07:32'),
(59, 128, 'Stakeholder', 'edit', 'Updated', 'Role Status', 'Active', 'Not Active', 'System', '2025-11-02 12:07:32', '2025-11-02 12:07:32'),
(59, 129, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Eve Davis', 'System', '2025-11-02 12:07:45', '2025-11-02 12:07:45'),
(59, 130, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Steward', 'System', '2025-11-02 12:07:45', '2025-11-02 12:07:45'),
(59, 131, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'System', '2025-11-02 12:07:45', '2025-11-02 12:07:45'),
(59, 132, 'Stakeholder', 'edit', 'Updated', 'Name', 'Eve Davis', 'esraa Johnson', 'System', '2025-11-02 12:08:05', '2025-11-02 12:08:05'),
(59, 133, 'Stakeholder', 'edit', 'Updated', 'Role Status', 'Active', 'Not Active', 'System', '2025-11-02 12:08:05', '2025-11-02 12:08:05'),
(59, 134, 'Stakeholder', 'edit', 'Updated', 'Name', 'esraa Johnson', 'Eve Davis', 'System', '2025-11-02 12:08:18', '2025-11-02 12:08:18'),
(60, 138, 'System', 'Details', 'Added', 'Short Name', NULL, 'dgdfghfghfgh', 'Alice Smith', '2025-11-02 12:58:21', '2025-11-02 12:58:21'),
(60, 139, 'System', 'Details', 'Added', 'Description', NULL, 'dfghfghdfghdfgh', 'Alice Smith', '2025-11-02 12:58:21', '2025-11-02 12:58:21'),
(60, 140, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-11-02 12:58:21', '2025-11-02 12:58:21'),
(60, 141, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'Alice Smith', '2025-11-02 12:58:21', '2025-11-02 12:58:21'),
(60, 142, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 143, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 144, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 145, 'System', 'Details', 'Added', 'Type', NULL, 'Infrastructure', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 146, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 147, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 148, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 149, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 12:58:22', '2025-11-02 12:58:22'),
(60, 150, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Active', 'Yes', 'System', '2025-11-02 12:58:27', '2025-11-02 12:58:27'),
(60, 151, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Eve Davis', 'System', '2025-11-02 12:58:50', '2025-11-02 12:58:50'),
(60, 152, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Steward', 'System', '2025-11-02 12:58:50', '2025-11-02 12:58:50'),
(60, 153, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'System', '2025-11-02 12:58:50', '2025-11-02 12:58:50'),
(60, 154, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Active', 'Yes', 'System', '2025-11-02 13:00:01', '2025-11-02 13:00:01'),
(60, 155, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Eve Davis', NULL, 'System', '2025-11-02 13:00:18', '2025-11-02 13:00:18'),
(60, 156, 'Stakeholder', 'delete', 'Deleted', 'Role', 'System Steward', NULL, 'System', '2025-11-02 13:00:18', '2025-11-02 13:00:18'),
(60, 157, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-02 13:00:18', '2025-11-02 13:00:18'),
(61, 165, 'System', 'Details', 'Added', 'Short Name', NULL, 'hfghfgh', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 166, 'System', 'Details', 'Added', 'Description', NULL, 'fghdfhfgh', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 167, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 168, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'No', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 169, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 170, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 171, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 172, 'System', 'Details', 'Added', 'Type', NULL, 'Service', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 173, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 174, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 175, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 176, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 16:58:13', '2025-11-02 16:58:13'),
(61, 177, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-02 16:58:32', '2025-11-02 16:58:32'),
(61, 178, 'Stakeholder', 'delete', 'Deleted', 'Role', 'System Business Owner', NULL, 'System', '2025-11-02 16:58:32', '2025-11-02 16:58:32'),
(61, 179, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-02 16:58:32', '2025-11-02 16:58:32'),
(61, 180, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'System', '2025-11-02 16:58:45', '2025-11-02 16:58:45'),
(61, 181, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'System', '2025-11-02 16:58:45', '2025-11-02 16:58:45'),
(61, 182, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-02 16:58:45', '2025-11-02 16:58:45'),
(61, 183, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'System', '2025-11-02 16:58:45', '2025-11-02 16:58:45'),
(61, 184, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Role', 'System', '2025-11-02 16:58:45', '2025-11-02 16:58:45'),
(61, 185, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-02 16:58:45', '2025-11-02 16:58:45'),
(61, 186, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Active', 'Yes', 'System', '2025-11-02 16:59:07', '2025-11-02 16:59:07'),
(62, 187, 'System', 'Details', 'Added', 'Short Name', NULL, 'system test 4', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 188, 'System', 'Details', 'Added', 'Long Name', NULL, 'long name for the sys objectttt', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 189, 'System', 'Details', 'Added', 'Description', NULL, '5/11', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 190, 'System', 'Details', 'Added', 'External', NULL, 'No', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 191, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'Yes', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 192, 'System', 'Details', 'Added', 'Parent Short Name', NULL, 'test3', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 193, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Public', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 194, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Active', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 195, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Decommissioned', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 196, 'System', 'Details', 'Added', 'Type', NULL, 'Application', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 197, 'System', 'Details', 'Status Change', 'Classification', NULL, 'Critical', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 198, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 199, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 200, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(62, 201, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-05 15:51:07', '2025-11-05 15:51:07'),
(63, 202, 'System', 'Details', 'Added', 'Short Name', NULL, 'system_test4', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 203, 'System', 'Details', 'Added', 'Long Name', NULL, 'longgg namee', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 204, 'System', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 205, 'System', 'Details', 'Added', 'Asset Id', NULL, '4444', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 206, 'System', 'Details', 'Added', 'URI', NULL, 'urlll', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 207, 'System', 'Details', 'Added', 'External', NULL, 'Yes', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 208, 'System', 'Details', 'Added', 'Automatically Control Local Rules', NULL, 'Yes', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 209, 'System', 'Details', 'Added', 'Parent Short Name', NULL, 'test3', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 210, 'System', 'Details', 'Status Change', 'Axon Viewing', NULL, 'Internal', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 211, 'System', 'Details', 'Status Change', 'Axon Status', NULL, 'Pending', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 212, 'System', 'Details', 'Status Change', 'Lifecycle', NULL, 'Maintenance', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 213, 'System', 'Details', 'Added', 'Type', NULL, 'Application', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 214, 'System', 'Details', 'Status Change', 'Classification', NULL, 'Medium', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 215, 'System', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 216, 'System', 'Details', 'Added', 'Confidentiality', NULL, '4', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 217, 'System', 'Details', 'Added', 'Integrity', NULL, '1', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 218, 'System', 'Details', 'Added', 'Availability', NULL, '1', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 219, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 220, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 221, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 10:22:00', '2025-11-09 10:22:00'),
(63, 222, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Fourth Test', 'System', '2025-11-09 10:23:05', '2025-11-09 10:23:05'),
(63, 223, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Role', 'System', '2025-11-09 10:23:05', '2025-11-09 10:23:05'),
(63, 224, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-09 10:23:05', '2025-11-09 10:23:05'),
(63, 225, 'System', 'Details', 'Updated', 'Description', '9/11', '9///11', 'Alice Smith', '2025-11-09 11:20:23', '2025-11-09 11:20:23'),
(63, 226, 'System', 'Details', 'Updated', 'Asset Id', '4444', NULL, 'Alice Smith', '2025-11-09 11:20:23', '2025-11-09 11:20:23'),
(63, 227, 'System', 'Details', 'Updated', 'Type', 'Application', 'Service', 'Alice Smith', '2025-11-09 11:20:23', '2025-11-09 11:20:23'),
(63, 228, 'System', 'Details', 'Status Change', 'Classification', 'Medium', NULL, 'Alice Smith', '2025-11-09 11:20:23', '2025-11-09 11:20:23'),
(63, 229, 'System', 'Details', 'Status Change', 'Lifecycle', 'Maintenance', 'Decommissioned', 'Alice Smith', '2025-11-09 11:22:45', '2025-11-09 11:22:45'),
(63, 230, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 12:19:48', '2025-11-09 12:19:48'),
(63, 231, 'Stakeholder', 'delete', 'Deleted', 'Role', 'System Business Owner', NULL, 'System', '2025-11-09 12:19:48', '2025-11-09 12:19:48'),
(63, 232, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 12:19:48', '2025-11-09 12:19:48'),
(63, 233, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'System', '2025-11-12 14:00:51', '2025-11-12 14:00:51'),
(63, 234, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Business Owner', 'System', '2025-11-12 14:00:51', '2025-11-12 14:00:51'),
(63, 235, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-12 14:00:51', '2025-11-12 14:00:51'),
(63, 236, 'System', 'Details', 'Updated', 'Short Name', 'system_test4', 'system_test44', 'Alice Smith', '2025-11-12 14:01:00', '2025-11-12 14:01:00'),
(63, 237, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-17 15:46:03', '2025-11-17 15:46:03'),
(63, 238, 'Stakeholder', 'delete', 'Deleted', 'Role', 'System Business Owner', NULL, 'System', '2025-11-17 15:46:03', '2025-11-17 15:46:03'),
(63, 239, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-17 15:46:03', '2025-11-17 15:46:03'),
(63, 240, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Fourth Test', NULL, 'System', '2025-11-17 15:46:03', '2025-11-17 15:46:03'),
(63, 241, 'Stakeholder', 'delete', 'Deleted', 'Role', 'System Role', NULL, 'System', '2025-11-17 15:46:03', '2025-11-17 15:46:03'),
(63, 242, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-17 15:46:03', '2025-11-17 15:46:03'),
(63, 243, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'System Role', 'Alice Smith', '2025-11-17 15:52:18', '2025-11-17 15:52:18'),
(63, 244, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-17 15:52:18', '2025-11-17 15:52:18'),
(63, 245, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-17 15:52:18', '2025-11-17 15:52:18');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `system_audit_history`
--
ALTER TABLE `system_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `system_audit_history`
--
ALTER TABLE `system_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=246;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `system_audit_history`
--
ALTER TABLE `system_audit_history`
  ADD CONSTRAINT `system_audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `system` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
